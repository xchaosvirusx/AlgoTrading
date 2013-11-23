package Algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import Connector.Connector.ACTION;
import Connector.Havelock;
import DataStructure.*;
import DataStructure.Order.TYPE;

public class MarketMaker extends Algorithm {
	/*
	 * how long to pause before checking the orderbook again
	 * set to 10 SEC
	 */
	public final static long PAUSE_TIME = 10*1000;
	
	/* 
	 * how long before reseting the random component of bid ask sizes
	 * set to 10 MIN
	 */
	public final static long RANDOM_RESET_TIME = 10*60*1000;
	
	public final static long RANDOM_RESET_IN_PAUSE_TIME_CYCLE = RANDOM_RESET_TIME/PAUSE_TIME;
	
	//threshold for the ratio of 7D volume to 1D volume, should be greater than this
	public final static long VOLUME_RATIO_THRESHOLD = 3;
	
	public final static double NEUTRAL_IDEAL_ASSET_VALUE_PERCENT = 0.15;
	//set the cap for the max ideal asset adjustment 
	public final static double MAX_IDEAL_ASSET_VALUE_PERCENT_ADJUSTMENT = 0.05;
	
	public final static double MAX_SIZE_ADJUSTMENT_FACTOR = 2;
	
	public final static int MIN_PROFIT_TO_FEES_MULTIPLE = 5;
	
	public final static int MAX_PROFIT_ADJUSTMENT_FACTOR = 2;
	
	public final static int MIN_AVERAGE_DAILY_VOL_IN_BTC = 10;
	
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
	private static Order[] findBestEligibleOrders(TreeSet<Order> orders, double threshold, Orders myOrders){
		
		int volumeSoFar = 0;
		Order[] result = new Order[2];
		Iterator<Order> iter = orders.iterator();
		
		//create dummy placeholder orders
		Order bestOrder = new Order(null, "", null, null, -1, 0, 0);
		Order secondBestOrder = new Order(null, "", null, null, -1, 0, 0);
		
		//find best eligible order
		while(iter.hasNext()&&volumeSoFar<=threshold
				&& myOrders.getOrder(bestOrder.id)==null){
			bestOrder = iter.next();
			volumeSoFar += bestOrder.quantity;
		}
		
		//remove the best eligible order quantity to find the second best
		volumeSoFar -= bestOrder.quantity;
		
		//find second best eligible order
		while(iter.hasNext()&&volumeSoFar<=threshold
				&& myOrders.getOrder(secondBestOrder.id)==null){
			secondBestOrder = iter.next();
			volumeSoFar += secondBestOrder.quantity;
		}
		
		result[0] = bestOrder;
		result[1] = secondBestOrder;
		
		return result;
	}
	
	private static double calculateAbsoluteSpread(Order bid, Order ask){
		return ask.price - bid.price;
	}
	
	private static double calculatePercentSpread(Order bid, Order ask){
		return calculateAbsoluteSpread(bid, ask)/((ask.price + bid.price)/2);
	}
	
	private static Order checkSide(Order newOrder, Orders myOrders, Order bestOrder, Order nextBestOrder, long curUnits, double avaliableBalance, Havelock hl){
		TYPE side = newOrder.type;
		String symbol = newOrder.symbol;
		/*
		 * IF the best order is mine, check if the order is in a good spot on the order book
		 * -should not be more than 1 tick more in price than the next best order 
		 * -the quantity on it should not be below MIN_DISPLAY_SIZE
		 */
		if(myOrders.getOrder(bestOrder.id)!=null){
			//replace the bestOrder from order book from the one we have
			//this is useful since bestOrder from order book doesn't have filled info
			//but our orders do..this step is necessary
			bestOrder = myOrders.getOrder(bestOrder.id);
			//look at the second lowest ask, see if we are 1 tick apart, if not cancel order
			double diff = Math.abs(bestOrder.price - nextBestOrder.price);
			if(diff > 2*Havelock.MIN_TICK){
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
				if(side == TYPE.BID){
					System.out.println("Symbol: "+ symbol + " Bid has been partially filled.");
				} else if(side == TYPE.ASK){
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
		if(myOrders.getOrder(bestOrder.id)==null){
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
			if(valid){
				newOrder = hl.createOrder(newOrder);
				
				if(!newOrder.id.equals("")){
					System.out.println("Creating new Order! " + newOrder.toString());
				} else {
					System.err.println("Failed to create new Order! " + newOrder.toString());
				}
			}
			
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
	private static double calculateSizeAdjustmentFactor(TYPE side, double assetValueToPortfolioRatio, double idealAssetValuePercent){
		
		//need this to make sure sizeAdjustmentFactor is between 0 and 2
		assetValueToPortfolioRatio = Math.min(assetValueToPortfolioRatio, 2*idealAssetValuePercent);
		/*
		 * negative devation means asset is underweight, so should buy more, so adjust bidSize up, askSize down
		 * positive devation means asset is overweight, so should sell more, so adjust askSize up, bidSize down
		 */
		double deviation = assetValueToPortfolioRatio - idealAssetValuePercent;
		
		double sizeAdjustmentFactor = -1;
		
		if(side == TYPE.BID){
			sizeAdjustmentFactor = 1 - deviation/idealAssetValuePercent;
		} else if(side == TYPE.ASK){
			sizeAdjustmentFactor = 1 + deviation/idealAssetValuePercent;
		}
		
		return MAX_SIZE_ADJUSTMENT_FACTOR*(sizeAdjustmentFactor/2);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int numSymbol = args.length;
		ArrayList<String> symbols = new ArrayList<String>();
		
		for(int i=0; i<numSymbol; i++){
			symbols.add(args[i]);
		}
		
		long counter = 0;
		Havelock hl = new Havelock();

		double totalFees = Havelock.BUY_FEE+Havelock.SELL_FEE;
		
		int[] bidSizeRandomComponents = new int[numSymbol];
		int[] askSizeRandomComponents = new int[numSymbol];
		
		//Infinite loop
		while(true){
		
			try{
				for(int symIndex = 0; symIndex < symbols.size(); symIndex++){
						String symbol = symbols.get(symIndex);
						//get symbol information, include some volume price stats
						SymbolInfo symbolInfo = hl.getSymbolInfo(symbol);
						//calculate some sanity checks first
						double volumeRatio = symbolInfo.SevenDayStats.vol/symbolInfo.OneDayStats.vol;
						/*
						 * we would expect the volume ratio is greater than the threshold
						 * otherwise the symbol might be new (ie IPO happened recently and we
						 * dont want to touch it)
						 */
						if(volumeRatio<VOLUME_RATIO_THRESHOLD){
							System.out.println("Volume Ratio requirement not met for " + symbol);
							continue;
						}
						
						/*
						 * check that average daily volume is greater than the min required
						 */
						double minDailyVolInBTC = symbolInfo.SevenDayStats.vol*symbolInfo.SevenDayStats.vwap/7;
						if(minDailyVolInBTC<MIN_AVERAGE_DAILY_VOL_IN_BTC){
							System.out.println("Average Daily Volume requirement not met for " + symbol);
							continue;
						}
						
						//get some basic asset price trend information (ie increasing recently or decreasing recently)
						//see if the 7 day vwap is higher or lower than 1 day vwap
						double vwapDiff = symbolInfo.OneDayStats.vwap-symbolInfo.SevenDayStats.vwap;
						double vwapDiffPct = vwapDiff/((symbolInfo.OneDayStats.vwap+symbolInfo.SevenDayStats.vwap)/2);
						
						double idealAssetValuePercentAdjustment = Math.signum(vwapDiffPct)*Math.min(Math.abs(vwapDiffPct),MAX_IDEAL_ASSET_VALUE_PERCENT_ADJUSTMENT);
						/* adjust the ideal asset value percent based on the neutral value + the adjustment which depends
						 * on the trend of the asset
						 * If price has been increasing, we should buy more (ie adjust the percent up) since we can benefit from
						 * price increase
						 * If price has been falling, we should buy less (ie adjust the percent down) since we can get hurt by
						 * the price drop
						 */
						double idealAssetValuePercent = NEUTRAL_IDEAL_ASSET_VALUE_PERCENT + idealAssetValuePercentAdjustment;
						
						//get portfolio information
						Portfolio portfolio = hl.getPortfolio();
						
						/* calculate the targe number of shares we want in our portfolio based on 
						 * the percent value we want the asset be worth in our portfolio
						 */
						double totalPortfolioMktValue = portfolio.getTotalPortfolioMarketValue();
						double valueOfAssetAtIdealPct = totalPortfolioMktValue*idealAssetValuePercent;
						long targetNumUnits = (long) (valueOfAssetAtIdealPct/symbolInfo.OneDayStats.vwap);
						
						//we want to achieve the target units in about 5 orders
						long minDisplaySize = targetNumUnits/5;
						long displaySizeRange = minDisplaySize/5;
						
						//get the current number of units we have already
						Position assetPosition = portfolio.getPosition(symbol);
						long curNumUnits = 0;
						if(assetPosition != null){
							curNumUnits = assetPosition.quantity;
						}
						
						//reset the size random components if we cycled enough times
						if(counter%RANDOM_RESET_IN_PAUSE_TIME_CYCLE == 0){
							bidSizeRandomComponents[symIndex] = (int)(Math.random() * (displaySizeRange+1));
							askSizeRandomComponents[symIndex] = (int)(Math.random() * (displaySizeRange+1));
						}
					
					
						//get open orders
						Orders orders = hl.getOrders(symbol);
						
						//Analyze current order book state
						OrderBook orderbook = hl.getOrderBook(symbol);
						
						TreeSet<Order> sortedAsks = orderbook.getBestAsk();
						TreeSet<Order> sortedBids = orderbook.getBestBid();
						
						long bidSize = minDisplaySize + bidSizeRandomComponents[symIndex];
						long askSize = minDisplaySize + askSizeRandomComponents[symIndex];
						
						double assetValueToPortfolioRatio = curNumUnits*symbolInfo.lastPrice/totalPortfolioMktValue;
						
						//Adjust bid and ask order size based on deviation of asset value % from ideal
						double bidSizeAdjustmentFactor = calculateSizeAdjustmentFactor(TYPE.BID,assetValueToPortfolioRatio,idealAssetValuePercent);
						bidSize = (long) (bidSize*bidSizeAdjustmentFactor);
						
						double askSizeAdjustmentFactor = calculateSizeAdjustmentFactor(TYPE.ASK,assetValueToPortfolioRatio,idealAssetValuePercent);
						askSize = (long) (askSize*askSizeAdjustmentFactor);
						
						/* Invert the size adjustment factor
						 * For example in bid case, if bidSizeAdjustmentFactor is large, we want the threshold to be lower
						 * since we want to buy earlier
						 * Similarly if the bidSizeAdjustmentFactor is small, we want a higher threshold since we don't want
						 * to buy that badly so we can afford to wait
						 */
						double bidThreshold = (MAX_SIZE_ADJUSTMENT_FACTOR-bidSizeAdjustmentFactor)*minDisplaySize;
						double askThreshold = (MAX_SIZE_ADJUSTMENT_FACTOR-askSizeAdjustmentFactor)*minDisplaySize;
						
						//best ask in position 0 and next best is position 1
						Order[] bestAsks= findBestEligibleOrders(sortedAsks, askThreshold, orders);
						//best bid in position 0 and next best is position 1
						Order[] bestBids = findBestEligibleOrders(sortedBids,bidThreshold, orders);
			
						double pctSpread = calculatePercentSpread(bestBids[0],bestAsks[0]);
						
						//market make only if spread is greater than MIN_PROFIT_TO_FEES_MULTIPLE times the total fees
						//profit check
						if(pctSpread>MIN_PROFIT_TO_FEES_MULTIPLE*totalFees){
							/* 
							 * adjust the sizes based on how profitable it is
							 * base case is 3 times the totalFees
							 */
							double excessProfit = pctSpread-MIN_PROFIT_TO_FEES_MULTIPLE*totalFees;
							double profitAdjustmentFactor = excessProfit/(MIN_PROFIT_TO_FEES_MULTIPLE*totalFees);
							profitAdjustmentFactor = Math.min(profitAdjustmentFactor, MAX_PROFIT_ADJUSTMENT_FACTOR);
							askSize = (long)(askSize*profitAdjustmentFactor);
							bidSize = (long)(bidSize*profitAdjustmentFactor);
							
							Order newBid = new Order(TYPE.BID, "", "", symbol, -1, bidSize, 0);
							Order newAsk = new Order(TYPE.ASK, "", "", symbol, -1, askSize, 0);
							
							//ask side
							newAsk = checkSide(newAsk, orders, bestAsks[0], bestAsks[1], curNumUnits, portfolio.balanceAvailable,hl);
							//bid side
							newBid = checkSide(newBid, orders, bestBids[0], bestBids[1], curNumUnits, portfolio.balanceAvailable,hl);
							
							//get the status of my orders currently
							orders = hl.getOrders(symbol);
							
							//clean up ask orders
							Set<Order> toKeepAsks = new HashSet<Order>();
							toKeepAsks.add(orders.getOrder(bestAsks[0].id));
							toKeepAsks.add(orders.getOrder(newAsk.id));
							cleanUpOrders(TYPE.ASK, orders, toKeepAsks, hl);
							
							//clean up bid orders
							Set<Order> toKeepBids = new HashSet<Order>();
							toKeepBids.add(orders.getOrder(bestBids[0].id));
							toKeepBids.add(orders.getOrder(newBid.id));
							cleanUpOrders(TYPE.BID, orders, toKeepBids, hl);
							
							String keyVars = "Symbol: " + symbol
									+" PctSpread: " + pctSpread*100
									+" MinSize: " + minDisplaySize
									+" ProfitAdjF: " + profitAdjustmentFactor
									+" BidSizeAdjF: " + bidSizeAdjustmentFactor
									+" AskSizeAdjF: " + askSizeAdjustmentFactor
									+" IdealPctAdj: " + idealAssetValuePercentAdjustment;
							System.out.println(keyVars);
						}
						
						
			
				}
				//catch any exception that might arise record it and try again...
			}catch(Exception e){
				e.printStackTrace();
			}		
			
			try {
				Thread.sleep(PAUSE_TIME);
				counter++;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
