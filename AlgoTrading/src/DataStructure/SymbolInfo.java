package DataStructure;

public class SymbolInfo {
	public String symbol="";
	public double lastPrice = -1;
	public long unitsOutstanding = -1;
	public PeriodStats OneDayStats = new PeriodStats();
	public PeriodStats SevenDayStats = new PeriodStats();
	public PeriodStats ThirtyDayStats = new PeriodStats();
	
	public String toString(){
		String result = "";
		result += "Symbol: " + symbol + "\n"
				+ "Last Price: " + lastPrice + "\n"
				+ "Units: " + unitsOutstanding + "\n"
				+ "1D Stats: \n" + OneDayStats.toString() + "\n"
				+ "7D Stats: \n" + SevenDayStats.toString() + "\n"
				+ "30D Stats: \n" + ThirtyDayStats.toString() + "\n";
				
		return result;
	}
	
	public class PeriodStats{
		public double min = -1;
		public double max = -1;
		public double vwap = -1;
		public long vol = -1;
		
		public String toString(){
			String result = "";
			result += "\t Min: " + min + "\n"
					+ "\t Max: " + max + "\n"
					+ "\t VWAP: " + vwap + "\n"
					+ "\t Vol: " + vol + "\n";
					
			return result;
		}
	}
}
