package Testing;

import Connector.Havelock;
import DataStructure.*;

public class HavelockTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Havelock hl = new Havelock();
		SymbolInfo info = hl.getSymbolInfo("SMG");
		print(info);
	}
	
	private static void print(Object stuff){
		System.out.println(stuff);
	}

}
