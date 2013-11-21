package DataStructure;

/**
 * represent a position in the portfolio
 * @author ~Tony~
 *
 */
public class Position {
	
	public String symbol = "";
	public String name = "";
	public long quantity = -1;
	public double bookValue = -1;
	
	public Position(String symbol, String name, long quantity, double bookValue){
		this.symbol=symbol;
		this.name=name;
		this.quantity=quantity;
		this.bookValue=bookValue;
	}
	
	public String toString(){
		String result = symbol + "\n" 
					+ name + "\n"
					+ quantity + "\n"
					+ bookValue + "\n";
		return result;
	}
}
