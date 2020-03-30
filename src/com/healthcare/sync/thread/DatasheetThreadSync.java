package com.healthcare.sync.thread;

import java.nio.file.FileSystems;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deep.util.http.HttpClientGenery;
import com.deep.util.http.HttpMethodEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.server.config.ConfigInitialServlet;
import com.healthcare.sync.implementation.DatasheetDaoImp;
import com.healthcare.sync.service.DatasheetDao;
import com.deep.auth.TokenHttp;
import com.deep.sync.resource.DatasheetResource;
import com.deep.sync.entity.CacheEntity;
import com.deep.sync.entity.DatasheetEntity;
import com.project.server.database.DriverManagerSync;

public class DatasheetThreadSync extends Thread {

	private static final Logger log = LoggerFactory.getLogger(DatasheetThreadSync.class);

	private DatasheetDao dao = new DatasheetDaoImp();

	private static HashMap<String, CacheEntity> cache =new HashMap<String, CacheEntity>();
	
	@Override
	public void run() {
		Connection con = null;
		List<DatasheetEntity> result = null;
		boolean use_queue = false;
		int interval_queue = Integer.valueOf(ConfigInitialServlet.sleep);
		int interval_email_send = 0;
		String fileSeparator = FileSystems.getDefault().getSeparator();
		while (true) {
			// ----------------------------------
			try {
				// ------------------------------------------------------
				while (true) {
					// log.debug("Ejecutando el hilo...");
					if (HttpClientGenery.verifyInternetConnection()) {// si hay internet
						if (true) {
							con = DriverManagerSync.getDataSourceSqlLite(
									ConfigInitialServlet.REAL_PATH + fileSeparator + "Data" + fileSeparator + "queue.sqlite");
							log.debug("CATALINA: " + System.getProperty("catalina.base"));
							// -------------------------------------------------------------------
							log.debug("LLAMADA DataSheet");
							// ----------------------
							try {
								ConfigInitialServlet.ico.read();
								result = this.dao.getDataSheetLst();
							} catch (Exception err) {
								log.error(err.getMessage());
							}
							// ----------------------
							ConfigInitialServlet.ico.sync();
							if (result != null) {
								try {
									// ------------
									this.verifyData(result, con);
									// -----------
									List<DatasheetEntity> listDeepSync = this.verifyQueueSent(con);
									// ----------------
									if (!listDeepSync.isEmpty()) {
										log.debug("numero de elementos enviados " + listDeepSync.size());
										log.debug("HAY EN LA COLA");
										TokenHttp service = null;
										MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<String, Object>();
										headers.add("x-apikey-erp",ConfigInitialServlet.token);
										service = new TokenHttp(HttpMethodEnum.POST, DatasheetResource.Erp_datasheet, headers,
												ConfigInitialServlet.endPoint);
										service.execute(listDeepSync);
	
										log.debug("Response -> " + service.getResponseCode());
										if(service.getResponseCode() == 200) {
											this.cleanQueue(con);
										}
										service.close();
									}
								} catch (Throwable err) {
									log.error(err.getMessage());
								} finally {
									try {
										if (con != null)
											con.close();
										result = null;
									} catch (SQLException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							}
							ConfigInitialServlet.ico.sleep();
							Thread.sleep(5000);
						} else {
							log.debug("la cola esta desactivada...");
						}
					} else {
						ConfigInitialServlet.ico.offline();
						log.debug("No hay internet...");
					}
					if (interval_queue != 0)
						Thread.sleep(interval_queue);// tiempo en mili-segundos
					else
						Thread.sleep(1000);// tiempo en mili-segundos
				}
			} catch (Throwable e) {
				log.debug("Error Thread: "+e.getMessage());
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					if (con != null)
						con.close();
					result = null;
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void verifyData(List<DatasheetEntity> result, Connection con) {
		log.debug("Verificar Datasheet Cache");
		PreparedStatement pstm = null;
		ResultSet rs = null;
		ObjectMapper mapper = new ObjectMapper();
		String jsonInString = null;
		try {
			log.debug("COMPROBANDO DATA MASTER");
			for (DatasheetEntity prod : result) {
				pstm = con.prepareStatement("SELECT id FROM tb_deep_data_sheet_queue where id = ?");
				pstm.setString(1, prod.getSku());
				rs = pstm.executeQuery();
				if (rs.next()) {
					/* No colocar en cola si esta pendiente actualización */
					continue;
				}
				// Object to JSON in String
				jsonInString = mapper.writeValueAsString(prod);
				//-------------
				CacheEntity rsc=cache.get(prod.getSku());
				if (rsc!=null) {
					//log.debug("EXISTE");
					if (!rsc.getCheck_sum()
							.equals(Base64.getEncoder().encodeToString(jsonInString.getBytes()))) {
						log.debug("CHECKSUM DIFERENTE: "+rsc.getId());
						rsc.setJsonObject(jsonInString);
						rsc.setCheck_sum(Base64.getEncoder().encodeToString(jsonInString.getBytes()));
						pstm = con.prepareStatement("INSERT INTO tb_data_master_queue(jsonObject)VALUES(?)");
						pstm.setString(1, jsonInString);
						pstm.execute();
					}
				} else {
					log.debug("Cache: "+prod.getSku());
					rsc=new CacheEntity();
					rsc.setId(prod.getSku());
					rsc.setJsonObject(jsonInString);
					rsc.setCheck_sum(Base64.getEncoder().encodeToString(jsonInString.getBytes()));
					pstm = con.prepareStatement("INSERT INTO tb_data_master_queue(jsonObject)VALUES(?)");
					pstm.setString(1, jsonInString);
					pstm.execute();
				}
				cache.put(prod.getSku(), rsc);
			}
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			log.error("CLOSED");
			log.error(e.getMessage());
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (pstm != null)
					pstm.close();
				mapper = null;
				jsonInString = null;
				result = null;
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private List<DatasheetEntity> verifyQueueSent(Connection con) {
		// MANDAMOS A DEEP
		PreparedStatement pstm = null;
		ResultSet rs = null;
		ObjectMapper mapper = null;
		List<DatasheetEntity> listDeepSync = new ArrayList<DatasheetEntity>();
		try {
			log.debug("DataSheet verify Queue");
			pstm = con.prepareStatement("SELECT jsonObject FROM tb_data_master_queue");
			rs = pstm.executeQuery();
			while (rs.next()) {
				mapper = new ObjectMapper();
				// Object to JSON in String
				DatasheetEntity obj = mapper.readValue(rs.getString("jsonObject"), DatasheetEntity.class);
				// ------------------------------------------------------
				listDeepSync.add(obj);
			}
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			log.error(e.getMessage());
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (pstm != null)
					pstm.close();
				mapper = null;
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return listDeepSync;
	}
	
	private void cleanQueue(Connection con) {
		PreparedStatement pstm = null;
		log.debug("DataSheet Clean Queue");
		try {
			pstm = con.prepareStatement("DELETE FROM tb_data_master_queue");
			pstm.execute();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			log.error(e.getMessage());
		} finally {
			try {
				if (pstm != null)
					pstm.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
