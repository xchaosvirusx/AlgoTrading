package DataStructure;

import java.util.TreeSet;
import DataStructure.Order.*;
import DataStructure.Order.TYPE;

public class OrderBook {
	private Orders allOrders = new Orders();
	private TreeSet<Order> bestBid = new TreeSet<Order>(new BestBidComparator());
	private TreeSet<Order> bestAsk = new TreeSet<Order>(new BestAskComparator());
	
	public static final int BEST_ORDERS_TO_KEEP_TRACK = 30;
	
	public void addBidOrder(String id, String date, String symbol, double price, long quantity, long filled){

		allOrders.addOrder(TYPE.BID,  id,  date,  symbol,  price,  quantity,  filled);
		
		Order newOrder = allOrders.getOrder(id);
		
		//update best20Bid
		bestBid.add(newOrder);
		if(bestBid.size()>BEST_ORDERS_TO_KEEP_TRACK){
			bestBid.remove(bestBid.last());
		}
	}
	
	public void addAskOrder(String id, String date, String symbol, double price, long quantity, long filled){
		
		allOrders.addOrder(TYPE.ASK,  id,  date,  symbol,  price,  quantity,  filled);
		
		Order newOrder = allOrders.getOrder(id);
		
		//update best10Ask
		bestAsk.add(newOrder);
		if(bestAsk.size()>BEST_ORDERS_TO_KEEP_TRACK){
			bestAsk.remove(bestAsk.last());
		}
	}
	
	
	public TreeSet<Order> getBestBid(){
		return bestBid;
	}

	public TreeSet<Order> getBestAsk(){
		return bestAsk;
	}
	
	public long getTotalVolume(TYPE type){
		return allOrders.getTotalVolume(type);
	}
	
	public String toString(){
		return allOrders.toString();
	}
}
