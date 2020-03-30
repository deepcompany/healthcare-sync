package com.healthcare.sync.implementation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.server.database.DriverManagerSync;
import com.deep.sync.entity.DatasheetEntity;
import com.healthcare.sync.service.DatasheetDao;

public class DatasheetDaoImp implements DatasheetDao {

	private Logger log = LoggerFactory.getLogger(DatasheetDaoImp.class);

	@Override
	public List<DatasheetEntity> getDataSheetLst() throws Throwable {
		double tax;
		log.debug(" Consultar items");
		List<DatasheetEntity> result = new ArrayList<DatasheetEntity>();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DriverManagerSync.getDataSource().getConnection();
			pstm = con.prepareStatement(
					"SELECT sku, price, tax FROM datasheet ");
			rs = pstm.executeQuery();
			while (rs.next()) {
				tax=rs.getDouble("tax")/100;
				DatasheetEntity prod = new DatasheetEntity();
				prod.setSku(rs.getString("sku"));
				prod.setPrice(Double.parseDouble(rs.getString("price")));
				prod.setSale_price(Double.parseDouble(rs.getString("price"))*(1+tax));
				prod.setTax(tax);
				result.add(prod);
			}
		} catch (Exception err) {
			log.error(err.getMessage());
			err.printStackTrace();
		} finally {
			if (con != null)
				con.close();
			if (pstm != null)
				pstm.close();
			if (rs != null)
				rs.close();
		}
		return result;
	}
}
