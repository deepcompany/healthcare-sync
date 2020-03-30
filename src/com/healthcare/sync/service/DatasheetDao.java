package com.healthcare.sync.service;

import java.util.List;

import com.deep.sync.entity.DatasheetEntity;

public interface DatasheetDao {

	List<DatasheetEntity> getDataSheetLst() throws Throwable;
}
