package com.tripcommon.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 天气信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeatherInfo {

    /**
     * 城市名称
     */
    private String city;

    /**
     * 天气状况（晴、多云、雨等）
     */
    private String condition;

    /**
     * 温度（摄氏度）
     */
    private String temperature;

    /**
     * 体感温度
     */
    private String feelsLike;

    /**
     * 湿度（百分比）
     */
    private String humidity;

    /**
     * 风向
     */
    private String windDir;

    /**
     * 风速（公里/小时）
     */
    private String windSpeed;

    /**
     * 天气数据观测时间
     */
    private String obsTime;

    /**
     * 备注信息
     */
    private String remark;
}
