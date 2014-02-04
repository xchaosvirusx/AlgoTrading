package Utilities;

import java.util.Properties;

public class AlgoConfig extends Properties {
	
	private static final long serialVersionUID = 1L;

	public void setProperty(String key, int value){
		setProperty(key,""+value);
	}
	
	public void setProperty(String key, long value){
		setProperty(key, ""+value);
	}
	
	public void setProperty(String key, double value){
		setProperty(key,""+value);
	}
	
	
	public int getIntProperty(String key){
		String value = getProperty(key);
		return Integer.parseInt(value);
	}
	
	public long getLongProperty(String key){
		String value = getProperty(key);
		return Long.parseLong(value);
	}
	
	public double getDoubleProperty(String key){
		String value = getProperty(key);
		return Double.parseDouble(value);
	}
}
