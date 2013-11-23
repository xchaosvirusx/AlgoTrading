package Connector;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import DataStructure.*;
import DataStructure.Order.TYPE;


public class Havelock implements Connector {

	/*Havelock trading API url*/
	private String url="https://www.havelockinvestments.com/api/index.php";
	
	/*Havelock API Key Nov 13, 2013*/
	private String key="TyKKZJPude23x59na273h4MstyPQc2txr48GDM3gttZfnZKFSG83BLjvAyBdrvSX";
	
	/*Http Connection*/
	HttpsURLConnection connection = null;
	
	/*key for the API response status to check if everything was okay*/
	public static final String STATUS = "status";
	
	/* possible status response */
	public static final String OK = "ok";
	public static final String ERROR = "error";
	
	/*trading fees and type*/
	public static final double BUY_FEE = 0;
	public static final double SELL_FEE = 0.004;
	
	public static final FEE FEE_TYPE = FEE.PERCENT;
	
	public static final double MIN_TICK = 0.00000001;
	
	/*All the possible Havelock API Commands*/
	public enum CMD{
		TICKER("ticker"),
		TICKERFULL("tickerfull"),
		ORDERBOOK("orderbook"),
		ORDERBOOKFULL("orderbookfull"),
		PORTFOLIO("portfolio"),
		BALANCE("balance"),
		ORDERS("orders"),
		TRANSACTION("transactions"),
		ORDERCREATE("ordercreate"),
		ORDERCANCEL("ordercancel");
		
		private CMD(final String text) {
	        this.text = text;
	    }

	    private final String text;

	    @Override
	    public String toString() {
	        return text;
	    }
	}
	
	//main method to interact with the Havelock API
	private String apiCall(CMD cmd, Map<String,String> variables){
		String result = "";
		try{		
			URL apiURL = new URL(url);
			connection = (HttpsURLConnection) apiURL.openConnection();
			//need to pretend to be the a browser, otherwise they won't grant access
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setInstanceFollowRedirects(false); 			
			connection.setRequestMethod("POST"); 
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
			connection.setRequestProperty("charset", "utf-8");
			/*construct the POST variables*/
			String urlParameters = "";
			urlParameters += "cmd=" + cmd + "&key=" + key;
			for(String key : variables.keySet()){
				urlParameters += "&" + key + "=" + variables.get(key);
			}
			
			// Send API request
			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setUseCaches (false);
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			
	        // Get the response
	        BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	        String line;
	        while ((line = rd.readLine()) != null) {
	            result += line;
	        }
	        wr.close();
	        rd.close();
	        connection.disconnect();
		}catch(Exception e){
			e.printStackTrace();
			result = null;
		}
		
		return result;
	}
	
	/*
	 * gets the portfolio information in a Portfolio object
	 */
	public Portfolio getPortfolio(){
		HashMap<String,String> var = new HashMap<String,String>();
		String result = apiCall(CMD.PORTFOLIO,var);
		JSONObject jsonPortfolio = new JSONObject(result);
		String status = jsonPortfolio.getString(STATUS);
		//check if request was successful
		if(!status.equals(OK)) return null;
		
		Portfolio portfolio = new Portfolio();
		
		//get position information
		
		JSONArray positions = new JSONArray();
		/* 
		 * try to get the array, but if there is no positions,
		 * there won't be an JSONArray object, so an exception will be thrown
		 */
		try{
			positions = jsonPortfolio.getJSONArray("portfolio");
		}catch(JSONException e){}
		
		for(int i =0; i<positions.length();i++){
			JSONObject pos = positions.getJSONObject(i);
			String symbol = pos.getString("symbol");
			String name = pos.getString("name");
			long quantity = pos.getLong("quantity");
			double bookValue = pos.getDouble("bookvalue");
			double marketValue = pos.getDouble("marketvalue");
			
			portfolio.addPosition(symbol, name, quantity, bookValue, marketValue);
		}
		
		//get balance information
		result = apiCall(CMD.BALANCE,var);
		JSONObject jsonBalance = new JSONObject(result);
		status = jsonBalance.getString(STATUS);
		//check if request was successful
		if(!status.equals(OK)) return null;
		
		JSONObject balance = jsonBalance.getJSONObject("balance");
		portfolio.balance = balance.getDouble("balance");
		portfolio.balanceAvailable = balance.getDouble("balanceavailable");
		
		return portfolio;
	}
	
	/*
	 * gets all the open orders
	 */
	public Orders getAllOrders(){
		return getOrders("*");
	}
	
	/*
	 * gets all open orders corresponding to symbol
	 * if symbol is * (wildcard), it gets all open orders
	 */
	public Orders getOrders(String symbol){
		symbol = symbol.trim();
		
		HashMap<String,String> var = new HashMap<String,String>();
		String result = apiCall(CMD.ORDERS,var);
		JSONObject jsonOrders = new JSONObject(result);
		String status = jsonOrders.getString(STATUS);
		//check if request was successful
		if(!status.equals(OK)) return null;
		
		Orders orders = new Orders();
		JSONArray ordersArray = new JSONArray();
		/* 
		 * try to get the array, but if there is no outstanding orders,
		 * there won't be an JSONArray object, so an exception will be thrown
		 */
		try{
			ordersArray = jsonOrders.getJSONArray("orders");
		}catch(JSONException e){}
		
		for(int i =0; i<ordersArray.length();i++){
			JSONObject order = ordersArray.getJSONObject(i);
			String sym = order.getString("symbol");
			
			//Check if symbol match or is the wildcard, if not in either case, move to next order
			if(!sym.equals(symbol) && !symbol.equals("*")) continue;
			
			String typeStr = order.getString("type");
			TYPE type = null;
			if(typeStr.equals("bid")){
				type=TYPE.BID;
			} else if(typeStr.equals("ask")){
				type=TYPE.ASK;
			} else {
				//not expecting this to happen so should just return null
				return null;
			}
			String id = order.getString("id");
			String date = order.getString("dt");

			double price = order.getDouble("price");
			long quantity = order.getLong("quantity");
			long filled = order.getLong("filled");
			
			orders.addOrder(type, id, date, sym, price, quantity, filled);
		}
		
		return orders;
	}
	
	/*
	 * gets the symbol (ticker) information
	 */
	public SymbolInfo getSymbolInfo(String symbol){
		symbol = symbol.trim();
		
		HashMap<String,String> var = new HashMap<String,String>();
		var.put("symbol", symbol);
		String result = apiCall(CMD.TICKERFULL,var);
		JSONObject symbolInfo = new JSONObject(result);
		
		SymbolInfo info = new SymbolInfo();
		info.symbol=symbol;
		
		//parse the symbol info object
		JSONObject jsonSymbolInfo = symbolInfo.getJSONObject(symbol);
		info.lastPrice = jsonSymbolInfo.getDouble("last");
		
		info.unitsOutstanding = jsonSymbolInfo.getLong("units");
		
		//process 1 day stats
		JSONObject OneDayStats = jsonSymbolInfo.getJSONObject("1d");
		info.OneDayStats.max = OneDayStats.getDouble("max");
		info.OneDayStats.min = OneDayStats.getDouble("min");
		info.OneDayStats.vwap = OneDayStats.getDouble("vwap");
		info.OneDayStats.vol = OneDayStats.getLong("vol");
		
		//process 7 day stats
		JSONObject SevenDayStats = jsonSymbolInfo.getJSONObject("7d");
		info.SevenDayStats.max = SevenDayStats.getDouble("max");
		info.SevenDayStats.min = SevenDayStats.getDouble("min");
		info.SevenDayStats.vwap = SevenDayStats.getDouble("vwap");
		info.SevenDayStats.vol = SevenDayStats.getLong("vol");
		
		//process 30 day stats
		JSONObject ThirtyDayStats = jsonSymbolInfo.getJSONObject("30d");
		info.ThirtyDayStats.max = ThirtyDayStats.getDouble("max");
		info.ThirtyDayStats.min = ThirtyDayStats.getDouble("min");
		info.ThirtyDayStats.vwap = ThirtyDayStats.getDouble("vwap");
		info.ThirtyDayStats.vol = ThirtyDayStats.getLong("vol");
		
		return info;
	}
	
	/*
	 * get the order book for a particular symbol
	 */
	public OrderBook getOrderBook(String symbol){
		symbol = symbol.trim();
		
		HashMap<String,String> var = new HashMap<String,String>();
		var.put("symbol", symbol);
		String result = apiCall(CMD.ORDERBOOKFULL,var);
		JSONObject jsonOrderBook = new JSONObject(result);
		String status = jsonOrderBook.getString(STATUS);
		//check if request was successful
		if(!status.equals(OK)) return null;
		
		OrderBook orderBook = new OrderBook();
		
		//process asks first
		JSONObject askBook = jsonOrderBook.getJSONObject("asks");
		for(Object id : askBook.keySet()){
			JSONObject order = askBook.getJSONObject((String) id);
			
			double price = order.getDouble("price");
			long quantity = order.getLong("amount");
			
			orderBook.addAskOrder((String) id, "", symbol, price, quantity, 0);
		}
		
		//process bids second
		JSONObject bidBook = jsonOrderBook.getJSONObject("bids");
		for(Object id : bidBook.keySet()){
			JSONObject order = bidBook.getJSONObject((String) id);
			
			double price = order.getDouble("price");
			long quantity = order.getLong("amount");
			
			orderBook.addBidOrder((String) id, "", symbol, price, quantity, 0);
		}
		
		return orderBook;
	}
	
	/*
	 * create order, return order with the id if successful
	 */
	public Order createOrder(Order order){
		
		String symbol = order.symbol;
		
		ACTION action = null;
		if(order.type == TYPE.BID){
			action = ACTION.BUY;
		} else if(order.type == TYPE.ASK){
			action = ACTION.SELL;
		} else {
			//not expecting this case
			System.err.println("Creating order and order type is not BID or ASK!");
			return order;
		}
		
		double price = order.price; 
		long quantity = order.quantity;
		String id = null;
		
		symbol = symbol.trim();
		
		HashMap<String,String> var = new HashMap<String,String>();
		var.put("symbol", symbol);
		var.put("price", ""+price);
		var.put("units", ""+quantity);
		if(action==ACTION.BUY){
			var.put("action","buy");
		} else if (action==ACTION.SELL){
			var.put("action","sell");
		} else {
			//not expecting so return null
			return order;
		}
		
		String result = apiCall(CMD.ORDERCREATE,var);
		JSONObject jsonStatus = new JSONObject(result);
		String status = jsonStatus.getString(STATUS);
		//check if request was successful
		if(!status.equals(OK)) return null;
		id = ""+jsonStatus.getLong("id");
		
		order.id = id;
		
		return order;
	}
	
	/*
	 * Cancels a particular order
	 */
	public boolean cancelOrder(Order order){
		String id = order.id;
		HashMap<String,String> var = new HashMap<String,String>();
		var.put("id", id);
		String result = apiCall(CMD.ORDERCANCEL,var);
		JSONObject jsonStatus = new JSONObject(result);
		String status = jsonStatus.getString(STATUS);
		//check if request was successful
		if(!status.equals(OK)) {
			return false;
		} else {
			return true;
		}
	}
}
