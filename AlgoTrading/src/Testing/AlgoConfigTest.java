package Testing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import Utilities.AlgoConfig;
import Algorithm.MarketMaker;

public class AlgoConfigTest {

	public static void main(String[] args) throws IOException {
		AlgoConfig config = new AlgoConfig();
		config.setProperty("API_KEY", "Paste API Key Here");
		config.setProperty(MarketMaker.SYMBOLS, "NEOBEE,HMF,HIF,AM100");
		config.setProperty(MarketMaker.DELIM, ",");
		config.setProperty(MarketMaker.PAUSE_TIME,60*1000);
		config.setProperty(MarketMaker.RANDOM_RESET_TIME,10*60*1000);
		config.setProperty(MarketMaker.NUM_STEP_TO_TARGET,9);
		config.setProperty(MarketMaker.BASE_PROFIT_TO_FEES_MULTIPLE,10);
		config.setProperty(MarketMaker.MAX_PORTFOLIO_VALUE_PERCENT,0.09);
		config.setProperty(MarketMaker.MIN_PORTFOLIO_VALUE_PERCENT,0.009);
		config.setProperty(MarketMaker.MA_DECAY_FACTOR,0.05);
		config.setProperty(MarketMaker.SPREAD_DROP_THRESHOLD, 3.0/4);
		config.setProperty(MarketMaker.IDEAL_TIGHTENING_FACTOR,9);
		config.setProperty(MarketMaker.BASE_VOLUME_HOLDING_MULTIPLE,20);
		config.setProperty(MarketMaker.MOON_SHOT_ORDER_PERCENT,0.2);
		config.setProperty(MarketMaker.MAX_ORDER_PER_PERIOD,5);
		config.setProperty(MarketMaker.PROFIT_REQ_ADJUSTMENT_NUMERATOR,393);
		config.setProperty(MarketMaker.MAX_PROFIT_REQUIREMENT,0.5);

		String filePath ="MarketMaker.config";
		File file = new File(filePath);
		if(!file.exists()) {
			file.createNewFile();
		}
		FileOutputStream out = new FileOutputStream(file);
		config.storeToXML(out, "Market Maker Config File");
	}

}
