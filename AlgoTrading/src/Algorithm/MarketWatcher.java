package Algorithm;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeSet;

import au.com.bytecode.opencsv.CSVWriter;
import Connector.Havelock;
import DataStructure.Order;
import DataStructure.OrderBook;
import DataStructure.SymbolInfo;
import DataStructure.SymbolInfo.PeriodStats;

/*
 * watches the Havelock market to gather market data for further anaylsis
 */
public class MarketWatcher extends Algorithm {
	
	public static final double MIN_IN_DAY = 24*60;
	
	public static final double MIN_IN_7_DAY = 7*MIN_IN_DAY;
	
	public static final double MIN_IN_30_DAY = 30*MIN_IN_DAY;
	
	public static final String DATA_FOLDER = "MarketData";
	
	public static final String CSV_HEADERS = "Time,LastPrice,MidPrice,PctSpread,Min1D,Max1D,VWAP1D,Vol1D,Min7D,Max7D,VWAP7D,Vol7D,Min30D,Max30D,VWAP30D,Vol30D";
	
	private static String recordPeriodStat(PeriodStats stat){
			String result = ",";
			result += stat.min + ","
					+ stat.max + ","
					+ stat.vwap + ","
					+ stat.vol;
			return result;
	}
	
	/*
	 * finds the order that best can be used to calculate the spread
	 */
	private static Order getSpreadOrder(TreeSet<Order> orders, double threshold){
		int volumeSoFar = 0;
		Order result = null;
		Iterator<Order> iter = orders.iterator();
		//find best eligible order
		
		while(iter.hasNext()&&volumeSoFar<=threshold){
			result = iter.next();
			volumeSoFar += result.quantity;
		}
		
		return result;
	}

	public static void main(String[] args) {
		int numSymbol = args.length;
		ArrayList<String> symbols = new ArrayList<String>();
		
		if(numSymbol==0){
			System.out.println("No Symbol given in argument. Exiting!");
			return;
		}
		
		for(int i=0; i<numSymbol; i++){
			symbols.add(args[i]);
		}
		
		Havelock hl = new Havelock();
		
		String pwd = System.getProperty("user.dir");
		
		Date date= null;
		String dateStr = null;
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		
		//Infinite loop
		while(true){
			date = new Date();
			dateStr = formatter.format(date.getTime());
			System.out.println(dateStr);
			try{
				for(int symIndex = 0; symIndex < symbols.size(); symIndex++){
						String symbol = symbols.get(symIndex);
						String fileName = symbol+".csv";
						String fullPathToFile = pwd + "\\" + DATA_FOLDER + "\\" + fileName;
						System.out.println(fullPathToFile);
						
						//get file, create if does not exist
						File dataFile = new File(fullPathToFile);
						boolean newFile = false;
						if(!dataFile.exists()) {
						    dataFile.createNewFile();
						    newFile=true;
						} 
						
						CSVWriter writer = new CSVWriter(new FileWriter(fullPathToFile,true));
						//write header if file is newly created
						if(newFile){
							writer.writeNext(CSV_HEADERS.split(","));
						}
						
						/*
						 * Get information from Havelock
						 */
						//get symbol and orderbook information
						SymbolInfo symbolInfo = hl.getSymbolInfo(symbol);
						OrderBook orderbook = hl.getOrderBook(symbol);
						
						//get sorted orderbook info
						TreeSet<Order> sortedAsks = orderbook.getBestAsk();
						TreeSet<Order> sortedBids = orderbook.getBestBid();

						//calculate the spread
						//set the threshold to be the average vol per 5 min the past 24 hours
						double threshold = 5*symbolInfo.OneDayStats.vol/MIN_IN_DAY;
						
						Order spreadAsk = getSpreadOrder(sortedAsks, threshold);
						Order spreadBid = getSpreadOrder(sortedBids, threshold);
						double midPrice = MarketMaker.calculateMidPrice(spreadBid, spreadAsk);
						double pctSpread = MarketMaker.calculatePercentSpread(spreadBid,spreadAsk);
						
						String entry = dateStr + ","
									+ symbolInfo.lastPrice + ","
									+ midPrice + ","
									+ pctSpread;
						
						entry += recordPeriodStat(symbolInfo.OneDayStats);
						entry += recordPeriodStat(symbolInfo.SevenDayStats);
						entry += recordPeriodStat(symbolInfo.ThirtyDayStats);
						
						writer.writeNext(entry.split(","));
						
						String keyVars =  "Symbol: %s"
								+" Threshold: %f"
								+" PctSpread: %.2f%n";
						System.out.format(keyVars,symbol,threshold,pctSpread*100);
						
						writer.close();
						
				}
				//catch any exception that might arise record it and try again...
			}catch(Exception e){
				e.printStackTrace();
			}		
			
			try {
				Thread.sleep(MarketMaker.PAUSE_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}


}
