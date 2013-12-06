package Algorithm;

import Connector.Havelock;
import DataStructure.*;

public class SandBox {

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
