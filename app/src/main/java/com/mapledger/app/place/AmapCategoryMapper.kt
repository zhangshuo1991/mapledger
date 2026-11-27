package com.mapledger.app.place

import com.mapledger.app.data.Category

/**
 * Maps Amap POI type codes (6 digits: big class / mid class / sub class) to ledger categories.
 * Reference: Amap POI 分类编码表. A POI can carry several codes joined by '|'; the first wins.
 */
object AmapCategoryMapper {
    fun map(typeCode: String?): Category {
        val code = typeCode?.split('|')?.firstOrNull()?.trim().orEmpty()
        if (code.length < 2) return Category.OTHER
        // Specific mid-class rules first.
        when (code.take(4)) {
            "0803", "0806", "0805", "0807", "0808" -> return Category.FUN   // 娱乐场所/影剧院/休闲场所/KTV…
            "1401" , "1412", "1413" -> return Category.FUN                     // 博物馆/展览/美术馆
            "0101", "0102" -> return Category.TRANSPORT                         // 加油站/加气站
            "0718" -> return Category.FUN                                       // 洗浴推拿 (休闲)
        }
        return when (code.take(2)) {
            "05" -> Category.FOOD
            "06" -> Category.SHOPPING
            "08" -> Category.FUN
            "11" -> Category.FUN
            "10" -> Category.HOTEL
            "15", "18", "01", "03", "04" -> Category.TRANSPORT
            "02" -> Category.SHOPPING
            "09" -> Category.HEALTH
            "07", "12", "16", "20" -> Category.LIFE
            "14" -> Category.EDU
            else -> Category.OTHER
        }
    }
}
