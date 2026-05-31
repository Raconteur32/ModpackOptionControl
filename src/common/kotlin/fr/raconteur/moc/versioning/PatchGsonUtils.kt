package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import java.math.BigDecimal
import java.math.BigInteger

fun GsonBuilder.registerPreciseNumberStrategy(): GsonBuilder =
    setObjectToNumberStrategy { reader ->
        val bd = BigDecimal(reader.nextString())
        if (bd.scale() <= 0) {
            val bi = bd.toBigInteger()
            if (bi >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) && bi <= BigInteger.valueOf(Int.MAX_VALUE.toLong()))
                bi.toInt()
            else
                bi
        } else {
            val d = bd.toDouble()
            if (BigDecimal.valueOf(d).compareTo(bd) == 0) d else bd
        }
    }
