package com.tripcommon.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POI（兴趣点）信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PoiInfo {

    /**
     * POI ID
     */
    private String id;

    /**
     * POI名称
     */
    private String name;

    /**
     * POI类型
     */
    private String type;

    /**
     * POI地址
     */
    private String address;

    /**
     * 城市
     */
    private String city;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 评分
     */
    private Double rating;

    /**
     * 营业时间
     */
    private String businessHours;

    /**
     * 电话
     */
    private String phone;

    /**
     * 备注信息
     */
    private String remark;
}
