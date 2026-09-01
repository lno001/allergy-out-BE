package com.allergyout.global.common;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageInfo {
	private int page;
	private int size;
	private int offset;
	private int totalElements;
	private int totalPages;

	public PageInfo(int page, int size) {
		this.page = page;
		this.size = size;
		this.offset = page * size;
	}

	public void calculateTotalPage(int totalElements) {
		if(this.size == 0) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		this.totalElements = totalElements;
		this.totalPages = (this.totalElements + this.size -1 ) / this.size;
	}

}
