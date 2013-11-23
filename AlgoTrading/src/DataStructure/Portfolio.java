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
	
	public void addPosition(String symbol, String name, long quantity, double bookValue, double marketValue){
		positions.put(symbol, new Position(symbol, name, quantity, bookValue, marketValue));
	}
	
	public Position getPosition(String symbol){
		return positions.get(symbol);
	}
	
	public Collection<Position> getAllPositions(){
		return positions.values();
	}
	
	public double getTotalPositionsMarketValue(){
		double totalValue = 0;
		for(String symbol : positions.keySet()){
			Position pos = positions.get(symbol);
			totalValue += pos.marketValue;
		}
		return totalValue;
	}
	
	public double getTotalPortfolioMarketValue(){
		double mktValue = balance;
		mktValue += getTotalPositionsMarketValue();
		return mktValue;
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
