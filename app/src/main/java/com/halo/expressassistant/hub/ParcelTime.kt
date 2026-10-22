package com.halo.expressassistant.hub

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object ParcelTime {
    val zone:ZoneId=ZoneId.of("Asia/Shanghai")
    fun parse(raw:String,now:Long=System.currentTimeMillis()):Long {
        val s=raw.trim()
        s.toLongOrNull()?.let {value ->
            val millis=if(value in 946684800L..4102444800L)value*1000 else value
            if(millis in 946684800000L..4102444800000L)return millis
        }
        runCatching {Instant.parse(s).toEpochMilli()}.getOrNull()?.let {return it}
        val normalized=s.replace("年","-").replace("月","-").replace("日","").replace("/","-").replace("T"," ")
        val dateTime=Regex("""(20\d{2})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""").find(normalized)
        fun from(m:MatchResult):Long=runCatching {
            LocalDateTime.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt(),m.groupValues[4].toIntOrNull() ?: 0,m.groupValues[5].toIntOrNull() ?: 0,m.groupValues[6].toIntOrNull() ?: 0).atZone(zone).toInstant().toEpochMilli()
        }.getOrDefault(0)
        if(dateTime!=null)return from(dateTime)
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for((word,offset) in listOf("今天" to 0L,"昨天" to 1L,"前天" to 2L))if(s.contains(word)) {
            val clock=Regex("""(\d{1,2}):(\d{2})""").find(s)
            return runCatching {today.minusDays(offset).atTime(clock?.groupValues?.get(1)?.toInt() ?: 0,clock?.groupValues?.get(2)?.toInt() ?: 0).atZone(zone).toInstant().toEpochMilli()}.getOrDefault(0)
        }
        val short=Regex("""(?<!\d)(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})(?!\d)""").find(normalized)
        if(short!=null)return runCatching {
            var date=LocalDateTime.of(today.year,short.groupValues[1].toInt(),short.groupValues[2].toInt(),short.groupValues[3].toInt(),short.groupValues[4].toInt())
            if(date.toLocalDate().isAfter(today.plusDays(1)))date=date.minusYears(1)
            date.atZone(zone).toInstant().toEpochMilli()
        }.getOrDefault(0)
        return 0
    }
    fun cutoff(days:Int,now:Long=System.currentTimeMillis())=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays((days-1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    fun arrivalLabel(at:Long,now:Long=System.currentTimeMillis()):String {
        if(at<=0 || at>now)return ""
        val days=ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(zone).toLocalDate(),Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        return if(days==0L)"今天到站" else "到站 $days 天"
    }
    fun format(at:Long)=if(at>0)DateTimeFormatter.ofPattern("MM-dd HH:mm").format(Instant.ofEpochMilli(at).atZone(zone)) else "日期未提供"
}
