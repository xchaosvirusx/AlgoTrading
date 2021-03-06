package Algorithm;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import Connector.Havelock;
import DataStructure.*;
import DataStructure.Order.TYPE;

import java.util.ArrayList;

import Utilities.AlgoConfig;

public class MarketMaker extends Algorithm {
	/*
	 * how long to pause before checking the orderbook again
	 * set to 60 SEC
	 */
	private static long pauseTime = 60*1000;
	public final static String PAUSE_TIME = "PAUSE_TIME";
	
	/* 
	 * how long before reseting the random component of bid ask sizes
	 * set to 10 MIN
	 */
	private static long randomResetTime = 10*60*1000;
	public final static String RANDOM_RESET_TIME = "RANDOM_RESET_TIME";
	
	private static long randomResetInPauseTimeCycle = randomResetTime/pauseTime;
	
	private static double maxSizeAdjustmentFactor = 2;
	public final static String MAX_SIZE_ADJUSTMENT_FACTOR = "MAX_SIZE_ADJUSTMENT_FACTOR";
	
	private static double numStepToTarget = 9;
	public final static String NUM_STEP_TO_TARGET = "NUM_STEP_TO_TARGET";
	
	private static int baseProfitToFeesMutiple = 10;
	public final static String BASE_PROFIT_TO_FEES_MULTIPLE = "BASE_PROFIT_TO_FEES_MULTIPLE";
	
	private static double maxPortfolioValuePercent = 0.09;
	public final static String MAX_PORTFOLIO_VALUE_PERCENT = "MAX_PORTFOLIO_VALUE_PERCENT";
	
	private static double minPortfolioValuePercent = 0.009;
	public final static String MIN_PORTFOLIO_VALUE_PERCENT = "MIN_PORTFOLIO_VALUE_PERCENT";
	/*
	 * How much the MA should decay by
	 */
	private static double maDecayFactor = 0.05;
	public final static String MA_DECAY_FACTOR = "MA_DECAY_FACTOR";
	
	private static double spreadDropThreshold = 3.0/4;
	public final static String SPREAD_DROP_THRESHOLD = "SPREAD_DROP_THRESHOLD";
	/*
	 * the power we raise the inverse of the best bid/ask adjustment factors by
	 * the higher the power, the less likely our holding will deviate from ideal
	 * value percent
	 */
	private static int idealTighteningFactor = 9;
	public final static String IDEAL_TIGHTENING_FACTOR = "IDEAL_TIGHTENING_FACTOR";
	
	/*
	 * number of cycles per day, one cycle is how long we pause for
	 */
	
	private static long baseVolumeHoldingMutiple = 20;
	public final static String BASE_VOLUME_HOLDING_MULTIPLE = "BASE_VOLUME_HOLDING_MULTIPLE";
	
	private static long cyclesPerDay = 24*60*60*1000/pauseTime;
	private static long cycleVolumeHoldingMutiple = baseVolumeHoldingMutiple*60*1000/pauseTime;
	
	/*
	 * percentage discount(for bid)/premium (for ask) for the moonshot orders
	 * (moonshot means shoot the moon, unlikely to be filled)
	 */
	private static double moonShotOrderPercent = 0.2;
	public final static String MOON_SHOT_ORDER_PERCENT = "MOON_SHOT_ORDER_PERCENT";
	
	private static int maxOrderPerPeriod = 5;
	public final static String MAX_ORDER_PER_PERIOD = "MAX_ORDER_PER_PERIOD";
	
	private static double profitReqAdjustmentNumerator = 393;
	public final static String PROFIT_REQ_ADJUSTMENT_NUMERATOR = "PROFIT_REQ_ADJUSTMENT_NUMERATOR";
	
	private static double maxProfitRequirement = 0.5;
	public final static String MAX_PROFIT_REQUIREMENT = "MAX_PROFIT_REQUIREMENT";
	
	private static String key = "";
	public final static String API_KEY = "API_KEY";
	
	public final static String SYMBOLS = "SYMBOLS";
	
	public final static String DELIM = "DELIM";
	
	
	
	/*
	 * Find the best eligible order we should peg to
	 * and the second best eligible order
	 * The best order is the first order on the book such that 
	 * the sum of volume of all orders before it is below the
	 * "threshold", but adding this order's quantity will
	 * go over the threshold OR the order is one of my order
	 * 
	 * The second best eligible order is the best eligible order
	 * not counting the best eligible order
	 */
	private static Order[] findBestEligibleOrders(TreeSet<Order> orders, double threshold, Order myOrder){
		
		int volumeSoFar = 0;
		Order[] result = new Order[2];
		Iterator<Order> iter = orders.iterator();
		
		//create dummy placeholder orders
		Order bestOrder = new Order(null, "", null, null, -1, 0, 0);
		Order secondBestOrder = new Order(null, "", null, null, -1, 0, 0);
		
		//find best eligible order
		while(iter.hasNext()&&volumeSoFar<=threshold && (!bestOrder.id.equals(myOrder.id))){
			bestOrder = iter.next();
			volumeSoFar += bestOrder.quantity;
		}
		
		//remove the best eligible order quantity to find the second best
		volumeSoFar -= bestOrder.quantity;
		
		//find second best eligible order
		while(iter.hasNext()&&volumeSoFar<=threshold && (!secondBestOrder.id.equals(myOrder.id))){
			secondBestOrder = iter.next();
			volumeSoFar += secondBestOrder.quantity;
		}
		
		result[0] = bestOrder;
		result[1] = secondBestOrder;
		
		return result;
	}
	
	
	private static Order[] findBestOrdersWithBetterPrice(TreeSet<Order> orders, double price){
	
		Order[] result = new Order[2];
		Iterator<Order> iter = orders.iterator();
		
		//create dummy placeholder orders
		Order bestOrder = new Order(null, "", null, null, -1, 0, 0);
		Order secondBestOrder = new Order(null, "", null, null, -1, 0, 0);
		
		//find best eligible order
		while(iter.hasNext()){
			bestOrder = iter.next();
			if(bestOrder.type == TYPE.BID){
				if(bestOrder.price<price) break;
			} else if(bestOrder.type == TYPE.ASK){
				if(bestOrder.price>price) break;
			}
		}
		
		//find best eligible order
		while(iter.hasNext()){
			secondBestOrder = iter.next();
			if(secondBestOrder.type == TYPE.BID){
				if(secondBestOrder.price<price) break;
			} else if(secondBestOrder.type == TYPE.ASK){
				if(secondBestOrder.price>price) break;
			}
		}
		
		result[0] = bestOrder;
		result[1] = secondBestOrder;
		
		return result;
	}
	
	public static double calculateMidPrice(Order bid, Order ask){
		return (ask.price + bid.price)/2;
	}
	
	public static double calculateAbsoluteSpread(Order bid, Order ask){
		return ask.price - bid.price;
	}
	
	public static double calculatePercentSpread(Order bid, Order ask){
		return calculateAbsoluteSpread(bid, ask)/calculateMidPrice(bid,ask);
	}
	
	public static boolean isValidOrder(Order order){
		return (order != null)&&(!order.id.equals("")) ? true:false;
	}
	
	private static Order sendOrder(Order newOrder, Order myOrder, Order bestOrder, Order nextBestOrder, long curUnits, double avaliableBalance, double midPrice, Havelock hl){
		TYPE side = newOrder.type;
		String symbol = newOrder.symbol;
		/*
		 * IF the best order is mine, check if the order is in a good spot on the order book
		 * -should not be more than 1 tick more in price than the next best order 
		 * -the quantity on it should not be below MIN_DISPLAY_SIZE
		 */
		if(bestOrder.id.equals(myOrder.id)){
			//replace the bestOrder from order book from the one we have
			//this is useful since bestOrder from order book doesn't have filled info
			//but our orders do..this step is necessary
			bestOrder = myOrder;
			//look at the second lowest ask, see if we are 1 tick apart, if not cancel order
			double diff = Math.abs(bestOrder.price - nextBestOrder.price);
			if(diff > 1.5*Havelock.MIN_TICK){
				if(side == TYPE.BID){
					System.out.println("Symbol: "+symbol + " Bid more than 1 tick apart! Diff: " + diff);
				} else if(side == TYPE.ASK){
					System.out.println("Symbol: "+symbol + " Ask more than 1 tick apart! Diff: " + diff);
				}
				hl.cancelOrder(bestOrder);
				/*
				 * since we cancelled our best order, the best order for reference is the
				 * old second best order
				 */
				bestOrder = nextBestOrder;
			//look at if anything has been filled, if so refresh the order
			} else if(bestOrder.filled > 0 || bestOrder.quantity!=newOrder.quantity){
				if(side == TYPE.BID && bestOrder.filled > 0){
					System.out.println("Symbol: "+ symbol + " Bid has been partially filled.");
				} else if(side == TYPE.ASK && bestOrder.filled > 0){
					System.out.println("Symbol: "+ symbol + " Ask has been partially filled.");
				}
				hl.cancelOrder(bestOrder);
				/*
				 * since we cancelled our best order, the best order for reference is the
				 * old second best order
				 */
				bestOrder = nextBestOrder;
			}
		}
		
		double price = -1;
		boolean valid = false;
		//check if bestOrder is mine if not, put an order to the top
		if(!bestOrder.id.equals(myOrder.id)){
			if(side == TYPE.ASK){
				price = bestOrder.price-Havelock.MIN_TICK;
				if(newOrder.quantity>0 && price>0){
					valid = true;
					//quantity adjustment in case
					if(newOrder.quantity>curUnits){
						newOrder.quantity=curUnits;
					}
				}
					
			} else if(side == TYPE.BID){
				price = bestOrder.price+Havelock.MIN_TICK;
				if(newOrder.quantity>0 && price>0){
					valid = true;
					//quantity adjustment incase
					if(newOrder.quantity*price>avaliableBalance){
						newOrder.quantity = (long) (avaliableBalance/price);
					}
				}
			} else {
				//not expecting this case
				System.err.println("NOT CHECKING BID OR ASK SIDE!! ERROR!!");
			}
			
			newOrder.price = price;
			/*
			 * Sanity check
			 * Bid order price should not be higher than midPrice 
			 * Ask order price should not be lower than midPrice
			 */
			if(side == TYPE.BID){
				if((newOrder.price - midPrice)> 2*Havelock.MIN_TICK){
					System.out.println("Bid Order Trying to cross spread!");
					newOrder = null;
				}
			} else if(side == TYPE.ASK){
				if((midPrice - newOrder.price)>2*Havelock.MIN_TICK){
					System.out.println("Ask Order Trying to cross spread!");
					newOrder = null;
				}
			}
			
			if(valid && (newOrder != null) && (newOrder.quantity>0)){
				
				newOrder = hl.createOrder(newOrder);
				
				if(isValidOrder(newOrder)){
					System.out.println("Creating new Order! " + newOrder.toString());
				} else {
					System.err.println("Failed to create new Order! Side: " + side );
				}
			} else {
				newOrder = null;
			}
			
		} else {
			newOrder = null;
		}
		
		return newOrder;
	}
	
	
	/*
	 * clean up and cancel all orders that are not part of toKeep
	 */
	private static void cleanUpOrders(TYPE side, Orders myOrders, Collection<Order> toKeep, Havelock hl){
		Collection<Order> orders = null;
		if(side == TYPE.ASK){
			orders = myOrders.getAllAskOrders();
		} else if(side == TYPE.BID){
			orders = myOrders.getAllBidOrders();
		}
		orders.removeAll(toKeep);
		//cancel other order
		for(Order order : orders){
			if(!hl.cancelOrder(order)){
				System.err.println("Failed to cancel order! " + order.toString());
			} else {
				System.out.println("Cancelling order " + order.toString());
			}
		}
	}
	
	/*
	 * generate a size adjustment factor between 0 and MAX_SIZE_ADJUSTMENT_FACTOR 
	 * to increase the reversion process (ie try to hit the ideal asset value percent)
	 */
	private static double calculateSizeAdjustmentFactor(TYPE side, long curNumUnits, long targetNumUnits){
		
		//need this to make sure sizeAdjustmentFactor is between 0 and 2
		curNumUnits = Math.min(curNumUnits, 2*targetNumUnits);
		/*
		 * negative deviation means asset is under weight, so should buy more, so adjust bidSize up, askSize down
		 * positive deviation means asset is over weight, so should sell more, so adjust askSize up, bidSize down
		 */
		double deviation = curNumUnits - targetNumUnits;
		
		double sizeAdjustmentFactor = -1;
		
		if(side == TYPE.BID){
			sizeAdjustmentFactor = 1 - deviation/targetNumUnits;
		} else if(side == TYPE.ASK){
			sizeAdjustmentFactor = 1 + deviation/targetNumUnits;
		}
		
		return maxSizeAdjustmentFactor*(sizeAdjustmentFactor/2);
	}
	
	private static void initializeTrackingOrderArray(Order[] orders){
		for(int i = 0; i <orders.length; i++){
			orders[i] = new Order(null, "init", "", "", -1, 0, 0);
		}
	}

	private static void resetCounter(int[] counter){
		for(int i = 0; i <counter.length; i++){
			counter[i] = 0;
		}
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 * @throws  
	 */
	public static void main(String[] args) throws IOException {
		if(args.length!=1){
			System.out.println("Need property file! Exiting!");
			return;
		}
		//load properties file
		FileInputStream configFileInputStream = new FileInputStream(args[0]);
		AlgoConfig config = new AlgoConfig();
		config.loadFromXML(configFileInputStream);
		
		int numSymbol = args.length;
		ArrayList<String> symbols = new ArrayList<String>();
		
		if(numSymbol==0){
			System.out.println("No Symbol given in argument. Exiting!");
			return;
		}
		
		for(int i=0; i<numSymbol; i++){
			symbols.add(args[i]);
		}
		
		long counter = 0;
		Havelock hl = new Havelock(key);

		double totalFees = Havelock.BUY_FEE+Havelock.SELL_FEE;
		
		Date date= null;
		 
		int[] bidSizeRandomComponents = new int[numSymbol];
		int[] askSizeRandomComponents = new int[numSymbol];
		double[] pctSpreadMA = new double[numSymbol];
		Order[] moonShotBids = new Order[numSymbol];
		Order[] moonShotAsks = new Order[numSymbol];
		Order[] mktMakingBids = new Order[numSymbol];
		Order[] mktMakingAsks = new Order[numSymbol];
		
		initializeTrackingOrderArray(moonShotBids);
		initializeTrackingOrderArray(moonShotAsks);
		initializeTrackingOrderArray(mktMakingBids);
		initializeTrackingOrderArray(mktMakingAsks);
		
		
		int[] newBidMktOrderCounter = new int[numSymbol];
		int[] newAskMktOrderCounter = new int[numSymbol];
		
		resetCounter(newBidMktOrderCounter);
		resetCounter(newAskMktOrderCounter);
		
		//Infinite loop
		while(true){
			date = new Date();
			System.out.println(new Timestamp(date.getTime()));
			try{
				//only need this once per cycle
				Portfolio portfolio = hl.getPortfolio();
				
				for(int symIndex = 0; symIndex < symbols.size(); symIndex++){
						String symbol = symbols.get(symIndex);
						System.out.println("******************** " + symbol + " ********************");
						/*
						 * Get information from Havelock
						 */
						//get symbol information, include some volume price stats
						SymbolInfo symbolInfo = hl.getSymbolInfo(symbol);
						//get portfolio information
						//get open orders
						Orders orders = hl.getOrders(symbol);
						//Analyze current order book state
						OrderBook orderbook = hl.getOrderBook(symbol);
						
						/* calculate the targe number of shares we want in our portfolio based on 
						 * liquidity of the stock measured by avgVolPerCycle based on 30 Day volume
						 */
						double avgVolPerCycle = symbolInfo.ThirtyDayStats.vol/(30.0*cyclesPerDay);
						long targetNumUnits = (long) Math.ceil(avgVolPerCycle*cycleVolumeHoldingMutiple);
						
						/*
						 * risk check so that target number of units doesn't become too big part of the portfolio
						 * 
						 * profit check so that target number of units is not too small to make any money
						 */
						double estimatedTargetValue = symbolInfo.OneDayStats.vwap*targetNumUnits;
						double portfolioMarketValue = portfolio.getTotalPortfolioMarketValue();
						double targetValueUpperLimit = portfolioMarketValue*maxPortfolioValuePercent;
						double targetValueLowerLimit = portfolioMarketValue*minPortfolioValuePercent;
						
						if(estimatedTargetValue>targetValueUpperLimit){
							long reducedTargetNumUnits = (long) Math.ceil(targetValueUpperLimit/symbolInfo.OneDayStats.vwap);
							System.out.format("Target Num Units: %d over value limit! Reducing to: %d %n",targetNumUnits,reducedTargetNumUnits);
							targetNumUnits = reducedTargetNumUnits;
						} else if(estimatedTargetValue<targetValueLowerLimit){
							long increasedTargetNumUnits = (long) Math.ceil(targetValueLowerLimit/symbolInfo.OneDayStats.vwap);
							System.out.format("Target Num Units: %d below value limit! Increasing to: %d %n",targetNumUnits,increasedTargetNumUnits);
							targetNumUnits = increasedTargetNumUnits;
						}
						
						//we want to achieve the target units in about NUM_STEP_TO_TARGET orders
						//minDisplaySize should be at least 1
						long minDisplaySize = (long) Math.max(targetNumUnits/numStepToTarget,1);
						long displaySizeRange = minDisplaySize/5;
						
						//get the current number of units we have already
						Position assetPosition = portfolio.getPosition(symbol);
						long curNumUnits = 0;
						if(assetPosition != null){
							curNumUnits = assetPosition.quantity;
						}
						
					
						//get sorted orderbook info
						TreeSet<Order> sortedAsks = orderbook.getBestAsk();
						TreeSet<Order> sortedBids = orderbook.getBestBid();
						
						double midPrice = calculateMidPrice(sortedBids.first(),sortedAsks.first());
						double topBidPrice = sortedBids.first().price;
						double topAskPrice = sortedAsks.first().price;
					
						
						
						System.out.println("~~Market Making~~");
						/*
						 * Market Making orders
						 */
						//reset the size random components if we cycled enough times
						//also reset the order counter
						if(counter%randomResetInPauseTimeCycle == 0){
							bidSizeRandomComponents[symIndex] = (int)(Math.random() * (displaySizeRange+1));
							askSizeRandomComponents[symIndex] = (int)(Math.random() * (displaySizeRange+1));
							resetCounter(newBidMktOrderCounter);
							resetCounter(newAskMktOrderCounter);
						}
						
						//set bid and ask size
						long bidSize = minDisplaySize + bidSizeRandomComponents[symIndex];
						long askSize = minDisplaySize + askSizeRandomComponents[symIndex];
						
						//calculate adjustment factor based on deviation of asset value % from ideal
						double bidSizeAdjustmentFactor = calculateSizeAdjustmentFactor(TYPE.BID,curNumUnits,targetNumUnits);
						double askSizeAdjustmentFactor = calculateSizeAdjustmentFactor(TYPE.ASK,curNumUnits,targetNumUnits);

						 //get the best bid and ask orders as reference orders (ie orders to peg to)
						/* Invert the size adjustment factor with IDEAL_TIGHTENING_FACTOR 
						 * For example in bid case, if bidSizeAdjustmentFactor is > 1, we want the threshold to be lower
						 * since we want to buy earlier
						 * Similarly if the bidSizeAdjustmentFactor is < 1, we want a higher threshold since we don't want
						 * to buy that badly so we can afford to wait
						 */
						double bestAskAdjustmentFactor = Math.max(askSizeAdjustmentFactor,0.01);
						bestAskAdjustmentFactor=1/Math.pow(bestAskAdjustmentFactor,idealTighteningFactor);
						
						double bestBidAdjustmentFactor = Math.max(bidSizeAdjustmentFactor,0.01);
						bestBidAdjustmentFactor=1/Math.pow(bestBidAdjustmentFactor,idealTighteningFactor);
						
						//best bid in position 0 and next best is position 1
						Order[] bestBids = findBestEligibleOrders(sortedBids, minDisplaySize*bestBidAdjustmentFactor, mktMakingBids[symIndex]);
						//best ask in position 0 and next best is position 1
						Order[] bestAsks = findBestEligibleOrders(sortedAsks, minDisplaySize*bestAskAdjustmentFactor, mktMakingAsks[symIndex]);
						
						//try to get a reasonable estimate of obtainable spread
						double spreadBidThreshold = (maxSizeAdjustmentFactor-bidSizeAdjustmentFactor)*minDisplaySize;
						double spreadAskThreshold = (maxSizeAdjustmentFactor-askSizeAdjustmentFactor)*minDisplaySize;
						//for calculating spread only
						Order[] spreadBids = findBestEligibleOrders(sortedBids, spreadBidThreshold, mktMakingBids[symIndex]);
						Order[] spreadAsks = findBestEligibleOrders(sortedAsks, spreadAskThreshold, mktMakingAsks[symIndex]);
						
						double actualOrderPctSpread = calculatePercentSpread(bestBids[0],bestAsks[0]);
						double obtainablePctSpread = calculatePercentSpread(spreadBids[0],spreadAsks[0]);
						
						double pctSpread = Math.min(actualOrderPctSpread, obtainablePctSpread);
						
						//calculate pctSpread MA
						if(pctSpreadMA[symIndex]==0){
							pctSpreadMA[symIndex]=pctSpread;
						} else {
							pctSpreadMA[symIndex]=(1-maDecayFactor)*pctSpreadMA[symIndex] + maDecayFactor*pctSpread;
						}
						
						/* 
						 * adjust the sizes
						 */
						bidSize = (long) Math.max(bidSize*bidSizeAdjustmentFactor,1);
						askSize = (long) Math.max(askSize*askSizeAdjustmentFactor,1);
						
						Order newBid = new Order(TYPE.BID, "", "", symbol, -1, bidSize, 0);
						Order newAsk = new Order(TYPE.ASK, "", "", symbol, -1, askSize, 0);
						
						//calculate profit requirement
						
						double profitRequirement = baseProfitToFeesMutiple*totalFees;
						
						profitRequirement = Math.min(profitRequirement, maxProfitRequirement);
						
						//spread drop check
						if(pctSpread>pctSpreadMA[symIndex]*spreadDropThreshold){
							//put in passive order if profit requirement is not met
							if(pctSpread<profitRequirement){
								System.out.println("Putting in Passive Orders");
								double bidPctThreshold = profitRequirement*(maxSizeAdjustmentFactor-bidSizeAdjustmentFactor);
								double bidPriceThreshold = midPrice*(1-bidPctThreshold);
								
								bestBids = findBestOrdersWithBetterPrice(sortedBids,bidPriceThreshold);
																	
	
								double askPctThreshold = profitRequirement*(maxSizeAdjustmentFactor-askSizeAdjustmentFactor);
								double askPriceThreshold = midPrice*(1+askPctThreshold);
								
								bestAsks = findBestOrdersWithBetterPrice(sortedAsks,askPriceThreshold);
							}
							
							//cool down if we have been placing too much orders
							if(newBidMktOrderCounter[symIndex]<maxOrderPerPeriod && (newBid!=null)){
								//bid side
								newBid = sendOrder(newBid, mktMakingBids[symIndex], bestBids[0], bestBids[1], curNumUnits, portfolio.balanceAvailable,midPrice, hl);
							}
							
							//cool down if we have been placing too much orders	
							if(newAskMktOrderCounter[symIndex]<maxOrderPerPeriod && (newAsk!=null)){
								//ask side
								newAsk = sendOrder(newAsk, mktMakingAsks[symIndex], bestAsks[0], bestAsks[1], curNumUnits, portfolio.balanceAvailable,midPrice, hl);
							}
							
							//update to keep track of the orders
							if(isValidOrder(newBid)){
								mktMakingBids[symIndex] = newBid;
								newBidMktOrderCounter[symIndex]++;
							}
							
							//update to keep track of the orders
							if(isValidOrder(newAsk)){
								mktMakingAsks[symIndex] = newAsk;
								newAskMktOrderCounter[symIndex]++;
							}
						}
						
						/*
						 * Moon Shot Orders
						 * 
						 * Moon shot orders are orders that might take a long time to get filled
						 * but can potentially provide good return
						 * used when other people dump their stock quickly and price drop by a lot
						 * or people bulk buy and price increases by a lot
						 */
						
						System.out.println("~~Moon Shot Orders~~");
						
						double moonShotRefPrice = symbolInfo.SevenDayStats.vwap;
						/*
						 * Moon Shot Bids
						 */
						// Only do moon shot bid if we don't have too much inventory
						if(curNumUnits<4*targetNumUnits){
							//Setup moon shot order
							long moonShotBidSize = targetNumUnits/2;
							Order moonShotBid = new Order(TYPE.BID, "", "", symbol, -1, moonShotBidSize, 0);
							//bid side
							double moonShotBidPriceThreshold = moonShotRefPrice*(1-moonShotOrderPercent);
							//Moon Shot bid orders should only be done if the threshold is actually lower than top bid price...
							while(moonShotBidPriceThreshold>topBidPrice){
								moonShotBidPriceThreshold = moonShotBidPriceThreshold*(1-moonShotOrderPercent);
							}
							
							Order[] moonShotBidRef = findBestOrdersWithBetterPrice(sortedBids,moonShotBidPriceThreshold);
							
							moonShotBid = sendOrder(moonShotBid , moonShotBids[symIndex], moonShotBidRef[0], moonShotBidRef[1], curNumUnits, portfolio.balanceAvailable, midPrice,hl);
							if(isValidOrder(moonShotBid)){
								moonShotBids[symIndex] = moonShotBid;
							}
						}
						
						/*
						 * Moon Shot Asks
						 */
						// Only do moon shot bid if we have excess units don't have too much inventory
						long excessUnits = curNumUnits - 2*(minDisplaySize+displaySizeRange);
						if(excessUnits > 0){
							//Setup moon shot order
							long moonShotAskSize = excessUnits/3;
							Order moonShotAsk = new Order(TYPE.ASK, "", "", symbol, -1, moonShotAskSize, 0);
							//ask side
							double moonShotAskPriceThreshold = moonShotRefPrice*(1+moonShotOrderPercent);
							//Moon Shot ask orders should only be done if the threshold is actually higher than top ask price...
							while(moonShotAskPriceThreshold<topAskPrice){
								moonShotAskPriceThreshold = moonShotAskPriceThreshold*(1+moonShotOrderPercent);
							}
							
							Order[] moonShotAskRef = findBestOrdersWithBetterPrice(sortedAsks,moonShotAskPriceThreshold);
							
							moonShotAsk = sendOrder(moonShotAsk , moonShotAsks[symIndex], moonShotAskRef[0], moonShotAskRef[1], curNumUnits, portfolio.balanceAvailable, midPrice,hl);
							if(isValidOrder(moonShotAsk)){
								moonShotAsks[symIndex] = moonShotAsk;
							}
						}
						
						System.out.println("~~Cleaning Up~~");
						//get the status of my orders currently
						orders = hl.getOrders(symbol);
						
						//clean up bid orders
						Set<Order> toKeepBids = new HashSet<Order>();
						
						// get full details on the market making bid order we are keeping track of
						if(orders.getOrder(mktMakingBids[symIndex].id)!= null){
							mktMakingBids[symIndex] = orders.getOrder(mktMakingBids[symIndex].id);
						}
						toKeepBids.add(mktMakingBids[symIndex]);
						
						// get full details on the moon shot bid order we are keeping track of
						if(orders.getOrder(moonShotBids[symIndex].id)!= null){
							moonShotBids[symIndex] = orders.getOrder(moonShotBids[symIndex].id);
						}
						toKeepBids.add(moonShotBids[symIndex]);
						cleanUpOrders(TYPE.BID, orders, toKeepBids, hl);	
						
						//clean up ask orders
						Set<Order> toKeepAsks = new HashSet<Order>();
						
						// get full details on the market making ask order we are keeping track of
						if(orders.getOrder(mktMakingAsks[symIndex].id)!= null){
							mktMakingAsks[symIndex] = orders.getOrder(mktMakingAsks[symIndex].id);
						}
						toKeepAsks.add(mktMakingAsks[symIndex]);
						
						// get full details on the moon shot ask order we are keeping track of
						if(orders.getOrder(moonShotAsks[symIndex].id)!= null){
							moonShotAsks[symIndex] = orders.getOrder(moonShotAsks[symIndex].id);
						}
						toKeepAsks.add(moonShotAsks[symIndex]);

						cleanUpOrders(TYPE.ASK, orders, toKeepAsks, hl);
						
						String keyVars =  "Symbol: %s"
								+" ProfitReq: %.2f"
								+" PctSpread: %.2f"
								+" PctSpreadMA: %.2f"
								+" MinSize: %d"
								+" BidSizeAdjF: %.2f" 
								+" BestBidAdjF: %.2f" 
								+" AskSizeAdjF: %.2f"
								+" BestAskAdjF: %.2f"
								+" TgtNumUnits: %d" 
								+" EstTgtValue: %.2f"
								+" TgtValueLimit: %.2f"
								+" NumNewBid: %d"
								+" NumNewAsk: %d"
								+" MSRefPrice: %.5f"
								+"%n";
						System.out.format(keyVars,symbol,profitRequirement*100,pctSpread*100,pctSpreadMA[symIndex]*100,minDisplaySize,
								bidSizeAdjustmentFactor,bestBidAdjustmentFactor,askSizeAdjustmentFactor,bestAskAdjustmentFactor,
								targetNumUnits,estimatedTargetValue,targetValueUpperLimit,newBidMktOrderCounter[symIndex],newAskMktOrderCounter[symIndex],moonShotRefPrice);
						
				}
				//catch any exception that might arise record it and try again...
			}catch(Exception e){
				e.printStackTrace();
			}		
			
			try {
				Thread.sleep(pauseTime);
				counter++;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
