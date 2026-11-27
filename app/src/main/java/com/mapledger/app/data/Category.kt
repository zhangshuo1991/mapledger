package com.mapledger.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Ledger categories. Stored in Room by [name]. Colors are deliberately muted so the single UI accent stays dominant. */
enum class Category(val label: String, val color: Color, val icon: ImageVector) {
    FOOD("餐饮", Color(0xFFF2994A), Icons.Rounded.Restaurant),
    FUN("娱乐", Color(0xFFA77BF3), Icons.Rounded.Movie),
    SHOPPING("购物", Color(0xFFEC6A8C), Icons.Rounded.ShoppingBag),
    TRANSPORT("交通", Color(0xFF3FA7D6), Icons.Rounded.DirectionsSubway),
    HOTEL("住宿", Color(0xFF5B8DEF), Icons.Rounded.Hotel),
    HEALTH("医疗", Color(0xFF4CB98A), Icons.Rounded.LocalHospital),
    LIFE("生活", Color(0xFF8E9AAF), Icons.Rounded.LocalLaundryService),
    EDU("教育", Color(0xFFD9A93A), Icons.Rounded.MenuBook),
    OTHER("其他", Color(0xFF9A9AA2), Icons.Rounded.Place);

    companion object {
        fun of(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
