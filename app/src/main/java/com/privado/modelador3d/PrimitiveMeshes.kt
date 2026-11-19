package com.privado.modelador3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object PrimitiveMeshes {
    fun create(kind: String): FloatArray = when (kind) {
        "Plano" -> plane()
        "Esfera" -> sphere()
        "Cilindro" -> cylinder(1f)
        "Cone" -> cylinder(0f)
        "Toro" -> torus()
        else -> cube()
    }

    private fun cube(): FloatArray {
        val out = ArrayList<Float>()
        val faces = arrayOf(
            floatArrayOf(-1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,1f,1f),
            floatArrayOf(-1f,-1f,-1f, -1f,1f,-1f, 1f,1f,-1f, 1f,-1f,-1f),
            floatArrayOf(-1f,1f,-1f, -1f,1f,1f, 1f,1f,1f, 1f,1f,-1f),
            floatArrayOf(-1f,-1f,-1f, 1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,1f),
            floatArrayOf(1f,-1f,-1f, 1f,1f,-1f, 1f,1f,1f, 1f,-1f,1f),
            floatArrayOf(-1f,-1f,-1f, -1f,-1f,1f, -1f,1f,1f, -1f,1f,-1f)
        )
        val colors = arrayOf(floatArrayOf(1f,.3f,.3f), floatArrayOf(.3f,1f,.4f), floatArrayOf(.3f,.5f,1f), floatArrayOf(1f,.8f,.2f), floatArrayOf(.8f,.3f,1f), floatArrayOf(.2f,.7f,1f))
        faces.forEachIndexed { i, face -> quad(out, face, colors[i]) }
        return out.toFloatArray()
    }

    private fun plane(): FloatArray {
        val out = ArrayList<Float>(); quad(out, floatArrayOf(-1f,0f,-1f, 1f,0f,-1f, 1f,0f,1f, -1f,0f,1f), floatArrayOf(.35f,.75f,1f)); return out.toFloatArray()
    }

    private fun sphere(): FloatArray {
        val out = ArrayList<Float>(); val rings = 10; val sides = 16
        for (r in 0 until rings) {
            val p0 = -PI.toFloat() / 2f + PI.toFloat() * r / rings; val p1 = -PI.toFloat() / 2f + PI.toFloat() * (r + 1) / rings
            for (s in 0 until sides) {
                val a0 = 2f * PI.toFloat() * s / sides; val a1 = 2f * PI.toFloat() * (s + 1) / sides
                quadPoints(out, point(p0,a0), point(p0,a1), point(p1,a1), point(p1,a0), floatArrayOf(.35f,.55f,1f))
            }
        }
        return out.toFloatArray()
    }

    private fun cylinder(topRadius: Float): FloatArray {
        val out = ArrayList<Float>(); val sides = 20; val color = floatArrayOf(.9f,.45f,.25f)
        for (s in 0 until sides) {
            val a0 = 2f * PI.toFloat() * s / sides; val a1 = 2f * PI.toFloat() * (s + 1) / sides
            val b0 = floatArrayOf(cos(a0), -1f, sin(a0)); val b1 = floatArrayOf(cos(a1), -1f, sin(a1)); val t0 = floatArrayOf(topRadius*cos(a0), 1f, topRadius*sin(a0)); val t1 = floatArrayOf(topRadius*cos(a1), 1f, topRadius*sin(a1))
            quad(out, floatArrayOf(b0[0],b0[1],b0[2], b1[0],b1[1],b1[2], t1[0],t1[1],t1[2], t0[0],t0[1],t0[2]), color)
            if (topRadius > 0f) { triangle(out, floatArrayOf(0f,1f,0f, t1[0],1f,t1[2], t0[0],1f,t0[2]), floatArrayOf(.3f,.7f,1f)) }
            triangle(out, floatArrayOf(0f,-1f,0f, b0[0],-1f,b0[2], b1[0],-1f,b1[2]), floatArrayOf(.3f,.7f,1f))
        }
        return out.toFloatArray()
    }

    private fun torus(): FloatArray {
        val out = ArrayList<Float>(); val rings = 20; val sides = 10; val major = 1.25f; val minor = .42f
        for (r in 0 until rings) for (s in 0 until sides) {
            val a0 = 2f*PI.toFloat()*r/rings; val a1 = 2f*PI.toFloat()*(r+1)/rings; val b0 = 2f*PI.toFloat()*s/sides; val b1 = 2f*PI.toFloat()*(s+1)/sides
            quadPoints(out, torusPoint(a0,b0), torusPoint(a0,b1), torusPoint(a1,b1), torusPoint(a1,b0), floatArrayOf(.8f,.35f,.9f))
        }
        return out.toFloatArray()
    }

    private fun point(p: Float, a: Float) = floatArrayOf(cos(p)*cos(a), sin(p), cos(p)*sin(a))
    private fun torusPoint(a: Float, b: Float) = floatArrayOf((1.25f+.42f*cos(b))*cos(a), .42f*sin(b), (1.25f+.42f*cos(b))*sin(a))
    private fun triangle(out: ArrayList<Float>, p: FloatArray, color: FloatArray) { for (i in 0 until 3) { val j=i*3; out.add(p[j]);out.add(p[j+1]);out.add(p[j+2]);out.add(color[0]);out.add(color[1]);out.add(color[2]) } }
    private fun quadPoints(out: ArrayList<Float>, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, color: FloatArray) { quad(out, floatArrayOf(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],d[0],d[1],d[2]), color) }
    private fun quad(out: ArrayList<Float>, p: FloatArray, color: FloatArray) { triangle(out,floatArrayOf(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8]),color); triangle(out,floatArrayOf(p[0],p[1],p[2],p[6],p[7],p[8],p[9],p[10],p[11]),color) }
}
