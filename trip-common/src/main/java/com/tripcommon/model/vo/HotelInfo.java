package com.tripcommon.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 酒店信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HotelInfo {

    /**
     * 酒店ID
     */
    private String id;

    /**
     * 酒店名称
     */
    private String name;

    /**
     * 城市
     */
    private String city;

    /**
     * 酒店地址
     */
    private String address;

    /**
     * 酒店价格（元/晚）
     */
    private double price;

    /**
     * 酒店评分（0-5分）
     */
    private Double rating;

    /**
     * 酒店类型（经济型、舒适型、高档型、豪华型）
     */
    private String type;

    /**
     * 酒店设施
     */
    private String facilities;

    /**
     * 酒店电话
     */
    private String phone;

    /**
     * 备注信息
     */
    private String remark;
}
