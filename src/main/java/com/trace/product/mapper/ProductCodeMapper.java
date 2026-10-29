package com.trace.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trace.product.dto.CodeWithProductVO;
import com.trace.product.entity.ProductCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductCodeMapper extends BaseMapper<ProductCode> {

    /**
     * 批量插入（一物一码异步生成使用）
     */
    int insertBatch(@Param("list") List<ProductCode> list);

    /**
     * 原子累加扫码次数，并维护首末扫码时间
     */
    @Update("UPDATE product_code SET scan_count = scan_count + 1, " +
            "first_scan_time = IFNULL(first_scan_time, NOW()), " +
            "last_scan_time = NOW() WHERE id = #{id}")
    int increaseScanCount(@Param("id") Long id);

    /** 作废状态下被扫码次数 +1 */
    @Update("UPDATE product_code SET void_scan_count = void_scan_count + 1, " +
            "last_scan_time = NOW() WHERE id = #{id}")
    int increaseVoidScanCount(@Param("id") Long id);

    /** 超限后继续被扫码次数 +1 */
    @Update("UPDATE product_code SET over_limit_scan_count = over_limit_scan_count + 1, " +
            "last_scan_time = NOW() WHERE id = #{id}")
    int increaseOverLimitScanCount(@Param("id") Long id);

    /**
     * 码列表分页（JOIN 产品表带出基础信息）
     */
    List<CodeWithProductVO> selectPageWithProduct(@Param("codeType") Integer codeType,
                                                   @Param("productId") Long productId,
                                                   @Param("batchNo") String batchNo,
                                                   @Param("keyword") String keyword,
                                                   @Param("status") Integer status,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    long countWithProduct(@Param("codeType") Integer codeType,
                          @Param("productId") Long productId,
                          @Param("batchNo") String batchNo,
                          @Param("keyword") String keyword,
                          @Param("status") Integer status);
}
