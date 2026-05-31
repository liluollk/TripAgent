package com.triptools.tools;

import com.tripcommon.model.vo.HotelInfo;
import com.tripcommon.model.vo.PoiInfo;
import com.tripcommon.model.vo.WeatherInfo;
import com.triptools.service.HotelService;
import com.triptools.service.PoiService;
import com.triptools.service.WeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 旅行工具定义 - 使用 @Tool 注解让 MCP Server 自动注册
 */
@Component
public class TripTools {

    private final WeatherService weatherService;
    private final PoiService poiService;
    private final HotelService hotelService;

    public TripTools(WeatherService weatherService, PoiService poiService, HotelService hotelService) {
        this.weatherService = weatherService;
        this.poiService = poiService;
        this.hotelService = hotelService;
    }

    @Tool(description = "获取指定城市的实时天气信息，包括温度、天气状况、湿度、风向等")
    public WeatherInfo getWeather(
            @ToolParam(description = "城市名称，如：杭州、北京、上海") String city
    ) {
        return weatherService.getWeather(city);
    }

    @Tool(description = "搜索城市中的旅游景点，返回景点名称、地址、类型等信息")
    public List<PoiInfo> searchAttractions(
            @ToolParam(description = "城市名称，如：杭州、北京") String city
    ) {
        return poiService.searchPoi(city, "旅游景点");
    }

    @Tool(description = "搜索城市中的酒店，返回酒店名称、价格、评分、地址等信息")
    public List<HotelInfo> searchHotels(
            @ToolParam(description = "城市名称，如：杭州、北京") String city
    ) {
        return hotelService.searchByCity(city);
    }

    @Tool(description = "搜索城市中的餐厅，返回餐厅名称、地址、类型等信息")
    public List<PoiInfo> searchRestaurants(
            @ToolParam(description = "城市名称，如：杭州、北京") String city
    ) {
        return poiService.searchPoi(city, "餐厅");
    }
}
