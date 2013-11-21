package DataStructure;

import java.util.Comparator;

public class Order {
	public enum TYPE{BID,ASK};
	
	public String id = "";
	public TYPE type = null;
	public String date = "";
	public String symbol = "";
	public double price = -1;
	public long quantity = -1;
	public long filled = -1;
	
	public Order(TYPE type, String id, String date, String symbol, double price, long quantity, long filled){
		this.type=type;
		this.id=id;
		this.date=date;
		this.symbol=symbol;
		this.price=price;
		this.quantity=quantity;
		this.filled=filled;
	}
	
	public String toString(){
		String result = "Symbol: " + symbol + ", " 
					+ "Type: " + type + ", "
					+ "ID: " + id + ", "
					+ "Date: " + date + ", "
					+ "Price: " + price + ", "
					+ "Quantity: " + quantity + ", "
					+ "Filled: " + filled 
					+ "\n";
		return result;
	}
	
	/*
	 * Used to compare bids
	 * Higher price are better
	 * want to return negative for better case
	 * 
	 * cannot return 0 since I use a TreeSet to order them,
	 * so no two orders can be "equal" or else it will not make
	 * the set
	 */
	public static class BestBidComparator implements Comparator<Order>{

		public int compare(Order order1, Order order2) {
			if(order1.price - order2.price < 0) return 1;
			if(order1.price - order2.price > 0) return -1;
			if(order1.quantity > order2.quantity){
				return -1;
			} else {
				return 1;
			}
		}
	}
	
	/*
	 * Used to compare asks
	 * Lower price are better
	 * want to return negative for better case
	 * 
	 * cannot return 0 since I use a TreeSet to order them,
	 * so no two orders can be "equal" or else it will not make
	 * the set
	 */
	public static class BestAskComparator implements Comparator<Order>{

		public int compare(Order order1, Order order2) {
			if(order1.price - order2.price < 0) return -1;
			if(order1.price - order2.price > 0) return 1;
			if(order1.quantity > order2.quantity){
				return -1;
			} else {
				return 1;
			}
		}
		
	}
}
