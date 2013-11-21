package DataStructure;

import java.util.Collection;
import java.util.HashMap;

/**
 * Represent a Portfolio of assets
 * @author ~Tony~
 *
 */
public class Portfolio {
	private HashMap<String,Position> positions = null;
	
	public double balance = -1;
	public double balanceAvailable = -1;
	
	public Portfolio(){
		positions = new HashMap<String,Position>();
	}
	
	public void addPosition(String symbol, String name, long quantity, double bookValue){
		positions.put(symbol, new Position(symbol, name, quantity, bookValue));
	}
	
	public Position getPosition(String symbol){
		return positions.get(symbol);
	}
	
	public Collection<Position> getAllPositions(){
		return positions.values();
	}
	
	public double getTotalPositionValues(){
		double totalValue = 0;
		for(String symbol : positions.keySet()){
			Position pos = positions.get(symbol);
			totalValue += pos.bookValue;
		}
		return totalValue;
	}
	
	public String toString(){
		String result = "";
		//construct balance string
		result += "Balance: " + balance + "\n"
				+"Balance Available: " + balanceAvailable + "\n\n";
		
		//construct position string
		for(String symbol : positions.keySet()){
			result += positions.get(symbol).toString() + "\n";
		}
		return result;
	}
}
