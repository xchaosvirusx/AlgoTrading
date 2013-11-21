package Algorithm;

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
	
	//minimum display size before refresh the peg order
	public final static long MIN_DISPLAY_SIZE = 50;
	
	//the range of random addition to display size before 
	public final static long DISPLAY_SIZE_RANGE = 10;
	
	public final static long MIN_VOLUME_THRESHOLD = MIN_DISPLAY_SIZE;
	
	public final static double IDEAL_ASSET_VALUE_PERCENT = 0.15;
	
	/*
	 * Find the best eligible order we should peg to
	 * and the second best eligible order
	 * The best order is the first order on the book such that 
	 * the sum of volume of all orders before it is below the
	 * MIN_VOLUME_THRESHOLD, but adding this order's quantity will
	 * go over the threshold OR one of my order
	 * 
	 * The second best eligible order is the best eligible order
	 * not counting the best eligible order
	 */
	private static Order[] findBestEligibleOrders(TreeSet<Order> orders, double thresholdAdjustmentFactor, Orders myOrders){
		
		int volumeSoFar = 0;
		Order[] result = new Order[2];
		Iterator<Order> iter = orders.iterator();
		
		//create dummy placeholder orders
		Order bestOrder = new Order(null, "", null, null, -1, 0, 0);
		Order secondBestOrder = new Order(null, "", null, null, -1, 0, 0);
		
		//find best eligible order
		while(iter.hasNext()&&volumeSoFar<=MIN_VOLUME_THRESHOLD*thresholdAdjustmentFactor
				&& myOrders.getOrder(bestOrder.id)==null){
			bestOrder = iter.next();
			volumeSoFar += bestOrder.quantity;
		}
		
		//remove the best eligible order quantity to find the second best
		volumeSoFar -= bestOrder.quantity;
		
		//find second best eligible order
		while(iter.hasNext()&&volumeSoFar<=MIN_VOLUME_THRESHOLD*thresholdAdjustmentFactor
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
	
	private static String checkSide(TYPE side, Orders myOrders, Order bestOrder, Order nextBestOrder, String symbol, long size, double sizeAdjustmentFactor, Havelock hl){
		/*
		 * IF the best order is mine, check if the order is in a good spot on the order book
		 * -should not be more than 1 tick more in price than the next best order 
		 * -the quantity on it should not be below MIN_DISPLAY_SIZE
		 */
		if(myOrders.getOrder(bestOrder.id)!=null){
			//look at the second lowest ask, see if we are 1 tick apart, if not cancel order
			double diff = Math.abs(bestOrder.price - nextBestOrder.price);
			if(diff > 2*Havelock.MIN_TICK){
				if(side == TYPE.BID){
					System.out.println("Bid more than 1 tick apart! Diff: " + diff);
				} else if(side == TYPE.ASK){
					System.out.println("Ask more than 1 tick apart! Diff: " + diff);
				}
				hl.cancelOrder(bestOrder.id);
				/*
				 * since we cancelled our best order, the best order for reference is the
				 * old second best order
				 */
				bestOrder = nextBestOrder;
			//look at the bestAsk quantity, if less than display size, cancel order
			} else if(bestOrder.quantity <= MIN_DISPLAY_SIZE*sizeAdjustmentFactor){
				if(side == TYPE.BID){
					System.out.println("Bid not at Display Size");
				} else if(side == TYPE.ASK){
					System.out.println("Ask not at Display Size");
				}
				hl.cancelOrder(bestOrder.id);
				/*
				 * since we cancelled our best order, the best order for reference is the
				 * old second best order
				 */
				bestOrder = nextBestOrder;
			}
		}
		
		String newOrderID = null;
		double price = -1;
		//check if bestOrder is mine if not, put an order to the top
		if(myOrders.getOrder(bestOrder.id)==null){
			if(side == TYPE.ASK){
				price = bestOrder.price-Havelock.MIN_TICK;
				newOrderID = hl.createOrder(symbol, ACTION.SELL, price , size);
			} else if(side == TYPE.BID){
				price = bestOrder.price+Havelock.MIN_TICK;
				newOrderID = hl.createOrder(symbol, ACTION.BUY, price , size);
			} else {
				//not expecting this case
				System.err.println("NOT CHECKING BID OR ASK SIDE!! ERROR!!");
			}
			
			if(newOrderID != null){
				System.out.println("Creating new Order! Price: " + price + " Side: " + side + " Quantity: " + size);
			} else {
				System.err.println("Failed to create new Order! Side:" + side);
			}
		}
		
		return newOrderID;
	}
	
	/*
	 * clean up and cancel all orders that are not part of toKeep
	 */
	private static void cleanUpOrders(TYPE side, Orders myOrders, Collection<String> toKeep, Havelock hl){
		Set<String> ids = null;
		if(side == TYPE.ASK){
			ids = myOrders.getAllAskOrderIDs();
		} else if(side == TYPE.BID){
			ids = myOrders.getAllBidOrderIDs();
		}
		ids.removeAll(toKeep);
		//cancel other order
		for(String id : ids){
			if(!hl.cancelOrder(id)){
				System.err.println("Failed to cancel order! ID: " + id + " Side: " + side);
			} else {
				System.out.println("Cancelling order ID: " + id + " Side: " + side);
			}
		}
	}
	
	private static double calculateSizeAdjustmentFactor(TYPE side, double assetValue, double portfolioValue){
		
		double assetValueToPortfolioRatio = assetValue/(portfolioValue);
		/*
		 * negative means asset is underweight, so should buy more, so adjust bidSize up, askSize down
		 * positive means asset is overweight, so should sell more, so adjust askSize up, bidSize down
		 */
		double deviation = assetValueToPortfolioRatio - IDEAL_ASSET_VALUE_PERCENT;
		
		double sizeAdjustmentFactor = -1;
		
		if(side == TYPE.BID){
			sizeAdjustmentFactor = 1 - deviation/IDEAL_ASSET_VALUE_PERCENT;
		} else if(side == TYPE.ASK){
			sizeAdjustmentFactor = 1 + deviation/IDEAL_ASSET_VALUE_PERCENT;
		}
		
		//the factor should be at least 0.1% (strictly positive)
		sizeAdjustmentFactor = Math.max(sizeAdjustmentFactor, 0.01);
		
		return sizeAdjustmentFactor;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String symbol = "XBOND";
		Havelock hl = new Havelock();
		
		double totalFees = Havelock.BUY_FEE+Havelock.SELL_FEE;
		
		long counter = 0;
		//generate initial size random component
		int bidSizeRandomComponent = (int)(Math.random() * (DISPLAY_SIZE_RANGE+1));
		int askSizeRandomComponent = (int)(Math.random() * (DISPLAY_SIZE_RANGE+1));
		
		while(true){
		
			try{
				//get open orders
				Orders orders = hl.getOrders(symbol);
				
				//Analyze current order book state
				OrderBook orderbook = hl.getOrderBook(symbol);
				
				TreeSet<Order> sortedAsks = orderbook.getBestAsk();
				TreeSet<Order> sortedBids = orderbook.getBestBid();
				
				long bidSize = MIN_DISPLAY_SIZE + bidSizeRandomComponent;
				long askSize = MIN_DISPLAY_SIZE + askSizeRandomComponent;
				
				//Adjust bid and ask order size based on deviation of asset value % from ideal
				Portfolio portfolio = hl.getPortfolio();
				Position assetPosition = portfolio.getPosition(symbol);
				
				
				double totalPortfolioValue = portfolio.balance + portfolio.getTotalPositionValues();
				double assetValue = assetPosition.bookValue;
				
				double bidSizeAdjustmentFactor = calculateSizeAdjustmentFactor(TYPE.BID,assetValue,totalPortfolioValue);
				bidSize = (long) (bidSize*bidSizeAdjustmentFactor)+1;
				
				double askSizeAdjustmentFactor = calculateSizeAdjustmentFactor(TYPE.ASK,assetValue,totalPortfolioValue);
				askSize = (long) (askSize*askSizeAdjustmentFactor)+1;
				
				//best ask in position 0 and next best is position 1
				Order[] bestAsks= findBestEligibleOrders(sortedAsks, 1/askSizeAdjustmentFactor, orders);
				//best bid in position 0 and next best is position 1
				Order[] bestBids = findBestEligibleOrders(sortedBids, 1/bidSizeAdjustmentFactor, orders);

				double pctSpread = calculatePercentSpread(bestBids[0],bestAsks[0]);
				System.out.println("Percent Spread: " + pctSpread*100);
				
				String newBidID = null;
				String newAskID = null;
				
				//market make only if spread is greater than 3 times the total fees
				//profit check
				if(pctSpread>2*totalFees){
					/* 
					 * adjust the sizes based on how profitable it is
					 * base case is 3 times the totalFees
					 */
					double profitAdjustmentFactor = pctSpread/(3*totalFees);
					askSize = (long)(askSize*profitAdjustmentFactor);
					bidSize = (long)(bidSize*profitAdjustmentFactor);
					askSizeAdjustmentFactor *= profitAdjustmentFactor;
					bidSizeAdjustmentFactor *= profitAdjustmentFactor;
					
					//ask side
					newAskID = checkSide(TYPE.ASK, orders, bestAsks[0], bestAsks[1], symbol, askSize, askSizeAdjustmentFactor, hl);
					//bid side
					newBidID = checkSide(TYPE.BID, orders, bestBids[0], bestBids[1], symbol, bidSize, bidSizeAdjustmentFactor, hl);
				}
				
				//get the status of my orders currently
				orders = hl.getOrders(symbol);
				
				//clean up ask orders
				Set<String> toKeepAskIDs = new HashSet<String>();
				toKeepAskIDs.add(bestAsks[0].id);
				toKeepAskIDs.add(newAskID);
				cleanUpOrders(TYPE.ASK, orders, toKeepAskIDs, hl);
				
				//clean up bid orders
				Set<String> toKeepBidIDs = new HashSet<String>();
				toKeepBidIDs.add(bestBids[0].id);
				toKeepBidIDs.add(newBidID);
				cleanUpOrders(TYPE.BID, orders, toKeepBidIDs, hl);
				

			//catch any exception that might arise record it and try again...
			}catch(Exception e){
				e.printStackTrace();
			}			
			
			try {
				Thread.sleep(PAUSE_TIME);
				counter++;
				//reset the size random components if we cycled enough times
				if(counter%RANDOM_RESET_IN_PAUSE_TIME_CYCLE == 0){
					bidSizeRandomComponent = (int)(Math.random() * (DISPLAY_SIZE_RANGE+1));
					askSizeRandomComponent = (int)(Math.random() * (DISPLAY_SIZE_RANGE+1));
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
