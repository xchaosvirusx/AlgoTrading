package DataStructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import DataStructure.Order.TYPE;

public class Orders {
	private HashMap<String,Order> bidOrders = null;
	private HashMap<String,Order> askOrders = null;
	
	public Orders(){
		bidOrders = new HashMap<String,Order>();
		askOrders = new HashMap<String,Order>();
	}
	
	public void addOrder(TYPE type, String id, String date, String symbol, double price, long quantity, long filled){
		Order order = new Order(type,  id,  date,  symbol,  price,  quantity,  filled);
		if(type == TYPE.BID){
			bidOrders.put(id, order);
		}else if(type == TYPE.ASK){
			askOrders.put(id, order);
		}
	}
	
	public Order getOrder(String id){
		Order result = null;
		Order bidOrder = bidOrders.get(id);
		Order askOrder = askOrders.get(id);
		if(bidOrder!=null){
			result = bidOrder;
		} else if(askOrder != null){
			result = askOrder;
		}
		return result;
	}
	
	public Collection<Order> getAllBidOrders(){
		return bidOrders.values();
	}
	
	public Set<String> getAllBidOrderIDs(){
		return bidOrders.keySet();
	}
	
	public Collection<Order> getAllAskOrders(){
		return askOrders.values();
	}
	
	public Set<String> getAllAskOrderIDs(){
		return askOrders.keySet();
	}
	
	public long getTotalVolume(TYPE type){
		long totalVolume = 0;
		Collection<Order> orderCollection = null;
		
		if(type == TYPE.BID){
			orderCollection = getAllBidOrders();
		} else if(type == TYPE.ASK){
			orderCollection = getAllAskOrders();
		} else {
			//not expecting, so return -1
			return -1;
		}
		
		for(Order order : orderCollection){
			totalVolume += order.quantity;
		}
		
		return totalVolume;
	}
	
	public String toString(){
		String result = "Bid Orders\n";
		for(String id : bidOrders.keySet()){
			result += bidOrders.get(id).toString() + "\n";
		}
		
		result += "\nAsk Orders\n";
		for(String id : askOrders.keySet()){
			result += askOrders.get(id).toString() + "\n";
		}
		
		return result;
	}
}
