package com.trace.product.mapper;

import com.trace.product.dto.StatItemVO;
import com.trace.product.dto.TopProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台统计聚合查询
 */
@Mapper
public interface StatsMapper {

    /**
     * 扫码次数最多的产品（Top N）
     */
    @Select("SELECT p.id AS id, p.product_no AS productNo, p.product_name AS productName, COUNT(s.id) AS scanCount "
            + "FROM scan_record s LEFT JOIN product p ON p.id = s.product_id "
            + "WHERE p.deleted = 0 "
            + "GROUP BY p.id, p.product_no, p.product_name "
            + "ORDER BY scanCount DESC LIMIT #{limit}")
    List<TopProductVO> topProducts(@Param("limit") int limit);

    /**
     * 扫码来源分布（0-未知 1-微信小程序 2-H5）
     */
    @Select("SELECT CAST(scan_source AS CHAR) AS name, COUNT(*) AS cnt FROM scan_record GROUP BY scan_source ORDER BY cnt DESC")
    List<StatItemVO> scanSourceStats();

    /**
     * 按日统计新增二维码数量
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS name, COUNT(*) AS cnt "
            + "FROM product_code WHERE create_time >= #{start} "
            + "GROUP BY name ORDER BY name")
    List<StatItemVO> codeTrend(@Param("start") LocalDateTime start);

    /**
     * 产品分类分布
     */
    @Select("SELECT category AS name, COUNT(*) AS cnt FROM product "
            + "WHERE deleted = 0 AND category IS NOT NULL AND category <> '' "
            + "GROUP BY category ORDER BY cnt DESC")
    List<StatItemVO> categoryStats();

    /**
     * 产品品牌分布
     */
    @Select("SELECT brand AS name, COUNT(*) AS cnt FROM product "
            + "WHERE deleted = 0 AND brand IS NOT NULL AND brand <> '' "
            + "GROUP BY brand ORDER BY cnt DESC")
    List<StatItemVO> brandStats();

    /**
     * 已被扫过的二维码数量（去重）
     */
    @Select("SELECT COUNT(DISTINCT code_id) FROM scan_record")
    Long scannedCodeCount();

    /**
     * 产品总浏览量
     */
    @Select("SELECT COALESCE(SUM(view_count), 0) FROM product WHERE deleted = 0")
    Long totalViewCount();

    /**
     * 扫码省份分布（未解析出的归入「未知」）
     */
    @Select("SELECT COALESCE(NULLIF(scan_province, ''), '未知') AS name, COUNT(*) AS cnt "
            + "FROM scan_record GROUP BY name ORDER BY cnt DESC")
    List<StatItemVO> provinceStats();

    /**
     * 扫码城市分布（Top N，未解析出的归入「未知」）
     */
    @Select("SELECT COALESCE(NULLIF(scan_city, ''), '未知') AS name, COUNT(*) AS cnt "
            + "FROM scan_record GROUP BY name ORDER BY cnt DESC LIMIT #{limit}")
    List<StatItemVO> cityStats(@Param("limit") int limit);

    /**
     * 已解析出地理位置的扫码数量
     */
    @Select("SELECT COUNT(*) FROM scan_record WHERE scan_province IS NOT NULL AND scan_province <> ''")
    Long geoResolvedCount();
}
