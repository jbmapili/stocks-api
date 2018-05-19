package ph.krisp.stocks.connection;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ph.krisp.stocks.model.Stock;
import ph.krisp.stocks.utils.CalcUtils;
import ph.krisp.stocks.utils.WebUtils;

/**
 * Class for connecting to the web app for scraping
 * 
 * @author kris.pagkaliwangan
 *
 */
public class WebCon {

	private static final Logger logger = Logger.getLogger("WebConnection");
	
	private static final String LOGIN_URL = "https://www.investagrams.com";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36";  
	private static final String REAL_TIME_MON_URL = "https://www.investagrams.com/Stock/RealTimeMonitoring";
	
	private WebCon() {}
	
	/**
	 * Logins to the given LOGIN_URL and returns the cookies to be used for
	 * authorized transactions
	 * 
	 * @return the cookies representing the login state
	 */
	public static Map<String, String> login() {
		logger.info("Logging in...");
		try {
			// get login page
			Response loginForm = Jsoup.connect(LOGIN_URL)
					.method(Connection.Method.GET)
					.userAgent(USER_AGENT)
					.followRedirects(true)
					.execute();
			Document loginDoc = loginForm.parse();
			// get cookies
			Map<String, String> cookies = loginForm.cookies();
			
			// get formData variables
    		String scriptManager = "LoginUserControlPanel$LoginUpdatePanel|LoginUserControlPanel$LoginButton";
    		String eventTarget = loginDoc.select("#__EVENTTARGET").first().attr("value");
    		String eventArgument = loginDoc.select("#__EVENTARGUMENT").first().attr("value");
    		String viewState = loginDoc.select("#__VIEWSTATE").first().attr("value");
    		String viewStateGenerator = loginDoc.select("#__VIEWSTATEGENERATOR").first().attr("value");
    		String eventValidation = loginDoc.select("#__EVENTVALIDATION").first().attr("value");
    		String rememberMe = "on";
    		String asyncPost = "true";
    		String loginButton = "Login";
    		
    		String username = WebUtils.getUsername();
    		String password = WebUtils.getPassword();
    		
    		Map<String, String> formData = new HashMap<String, String>();
    		formData.put("ScriptManager", scriptManager);
    		formData.put("__EVENTTARGET", eventTarget);
    		formData.put("__EVENTARGUMENT", eventArgument);
    		formData.put("__VIEWSTATE", viewState);
    		formData.put("__VIEWSTATEGENERATOR", viewStateGenerator);
    		formData.put("__EVENTVALIDATION", eventValidation);
    		formData.put("LoginUserControlPanel$RememberMe", rememberMe);
    		formData.put("__ASYNCPOST", asyncPost);
    		formData.put("LoginUserControlPanel$LoginButton", loginButton);
    		
    		formData.put("LoginUserControlPanel$Username", username);
    		formData.put("LoginUserControlPanel$Password", password);
			
    		Response homePage = Jsoup.connect(LOGIN_URL)  
   		         .cookies(cookies)  
   		         .data(formData)  
   		         .method(Connection.Method.POST)  
   		         .userAgent(USER_AGENT)
   		         .execute(); 
    		
    		if(homePage.body().contains("pageRedirect")) {
    			// update cookies
        		logger.info("Logging in successful!");
        		return homePage.cookies();
    		}
    		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		logger.info("Login unsuccessful!");
		return null;
	}
	
	/**
	 * Retrieves all the stock codes in the real time monitoring page of the
	 * Investagrams with their corresponding information
	 * 
	 * @param cookies
	 *            the authorized cookies
	 * @return a map containing all the stock codes paired to its stock
	 *         information
	 */
	public static Map<String, Stock> getAllStockInfo(Map<String, String> cookies) {
		Map<String, Stock> stockInfo = new HashMap<>();
		
		// check if cookies are valid
		if(cookies.size() == 0 || cookies == null) {
			return stockInfo;
		}
		
		try {
			Document stockDoc = getDocument(REAL_TIME_MON_URL, cookies);
			
			// extract all stock codes
			Elements table = stockDoc.body()
					.select("#StockQuoteTable > tbody > tr");

			for (Element row : table) {
				Stock stock = parseStockInfo(row);
				stockInfo.put(stock.getCode(), stock);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
		return stockInfo;
	}
	
	/**
	 * Parses all stock information for the particular row
	 * 
	 * @param row
	 *            the row to be parsed for a particular stock
	 * @return the object containing the stock information
	 */
	private static Stock parseStockInfo(Element row) {
		Elements rowData = row.select("td");
		
		// 0 - ignore
		
		// 1 - stock code
		String code = rowData.get(1).select("a").first().ownText();
		// 2 - close
		BigDecimal close = CalcUtils.parseNumber(rowData.get(2).ownText());
		// 3 - change
		BigDecimal change = CalcUtils.parseNumber(rowData.get(3).ownText());
		// 4 - %change
		BigDecimal percentChange = CalcUtils.parseNumber(rowData.get(4).ownText())
									.divide(new BigDecimal("100"));
		// 5 - open
		BigDecimal open = CalcUtils.parseNumber(rowData.get(5).ownText());
		// 6 - low
		BigDecimal low = CalcUtils.parseNumber(rowData.get(6).ownText());
		// 7 - high
		BigDecimal high = CalcUtils.parseNumber(rowData.get(7).ownText());
		// 8 - previous close
		BigDecimal previousClose = CalcUtils.parseNumber(rowData.get(8).ownText());
		// 9 - volume
		BigDecimal volume = CalcUtils.parseNumber(rowData.get(9).attr("data-sort")).stripTrailingZeros();
		// 10 - value
		BigDecimal value = CalcUtils.parseNumber(rowData.get(10).attr("data-sort")).stripTrailingZeros();

		return new Stock(code, close, change, percentChange, open, low, high, previousClose, volume, value);
	}
	
	/**
	 * Gets the document using the authorized cookies
	 * 
	 * @param url
	 *            the website url
	 * @param cookies
	 *            the authorized cookies
	 * @return the document
	 * @throws IOException
	 */
	private static Document getDocument(String url, Map<String, String> cookies) throws IOException {
		return Jsoup.connect(url)  
		         .cookies(cookies)  
		         .userAgent(USER_AGENT)  
		         .get();
	}
}
