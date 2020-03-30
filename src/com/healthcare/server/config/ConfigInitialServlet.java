package com.healthcare.server.config;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deep.auth.TokenServices;
import com.deep.system.Icon;
import com.deep.auth.TokenDaoImp;
import com.deep.auth.TokenEntity;
import com.deep.auth.TokenHttp;
import com.deep.util.http.HttpMethodEnum;
import com.healthcare.sync.thread.DatasheetThreadSync;
import com.healthcare.sync.thread.MainThreadStatic;

@WebServlet(name="ConfigLoadServletShopify", urlPatterns="/config/shopify",loadOnStartup=0)
public class ConfigInitialServlet extends HttpServlet{
	private Logger log = LoggerFactory.getLogger(ConfigInitialServlet.class);
	
	public static String REAL_PATH;
	
	public static String token;
	
	public static String endPoint;
	
	public static String sleep;
	
	public static Icon ico;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		// TODO Auto-generated method stub
		super.init(config);
		ico=new Icon(config.getServletContext().getRealPath("/"));	
		try {
			TokenEntity login=TokenDaoImp.auth(config,ico);
			if(login.isAuth()) {
				token=login.getKey();
				this.params(config);
				REAL_PATH = config.getServletContext().getRealPath("/");
				try {
					//------------------------------------------------------------------------
//					log.debug("start");
					if(MainThreadStatic.Values.hilo==null) {
						System.out.println("Hilo DataSheet");
						MainThreadStatic.Values.hilo = new DatasheetThreadSync();
						MainThreadStatic.Values.hilo.start();
					}
					//------------------------------------------------------------------------	
				}catch (Throwable e) {
					// TODO Auto-generated catch block
					log.error("**** Error Config load",e);
				}finally{

				}
			}else {
				log.error("**** Error Config load token");
			}
		} catch (Throwable e1) {
			// TODO Auto-generated catch block
			log.error("**** Error Config load",e1);
		}finally{
			
		}

	}
	private boolean params(ServletConfig config) throws Throwable {
		endPoint=config.getServletContext().getInitParameter("endPoint");
		sleep=config.getServletContext().getInitParameter("sleep");
		if(sleep==null)sleep="0";
		return true;
	}
}
