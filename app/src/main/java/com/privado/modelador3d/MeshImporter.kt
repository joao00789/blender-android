package com.privado.modelador3d

import java.io.File
/** Importadores pequenos e sem dependências para formatos abertos comuns. */
object MeshImporter {
    fun read(file: File): FloatArray? = runCatching {
        when (file.extension.lowercase()) {
            "obj" -> readObj(file)
            "stl" -> readAsciiStl(file)
            "ply" -> readAsciiPly(file)
            else -> null
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun readObj(file: File): FloatArray {
        val vertices = mutableListOf<FloatArray>()
        val triangles = mutableListOf<FloatArray>()
        file.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("v ") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) vertices += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                }
                line.startsWith("f ") -> {
                    val refs = line.substring(2).trim().split(Regex("\\s+"))
                        .mapNotNull { it.substringBefore('/').toIntOrNull() }
                        .map { if (it < 0) vertices.size + it else it - 1 }
                    for (i in 1 until refs.size - 1) {
                        listOf(refs[0], refs[i], refs[i + 1]).forEach { index -> vertices.getOrNull(index)?.let { triangles += it } }
                    }
                }
            }
        }
        return interleave(triangles)
    }

    private fun readAsciiStl(file: File): FloatArray {
        val vertices = mutableListOf<FloatArray>()
        file.forEachLine { raw ->
            val p = raw.trim().split(Regex("\\s+"))
            if (p.firstOrNull() == "vertex" && p.size >= 4) vertices += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
        }
        return interleave(vertices)
    }

    private fun readAsciiPly(file: File): FloatArray {
        val lines = file.readLines()
        val endHeader = lines.indexOfFirst { it.trim() == "end_header" }
        if (endHeader < 0) return FloatArray(0)
        var vertexCount: Int? = null
        for (headerLine in lines.take(endHeader)) {
            val match = Regex("element\\s+vertex\\s+(\\d+)").find(headerLine)
            if (match != null) {
                vertexCount = match.groupValues[1].toIntOrNull()
                break
            }
        }
        val count = vertexCount ?: return FloatArray(0)
        val vertices = lines.drop(endHeader + 1).take(count).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 3) floatArrayOf(p[0].toFloat(), p[1].toFloat(), p[2].toFloat()) else null
        }
        // Sem faces, cada vértice vira um ponto visível no modo de edição.
        return interleave(vertices)
    }

    private fun interleave(vertices: List<FloatArray>): FloatArray {
        if (vertices.isEmpty()) return FloatArray(0)
        val result = FloatArray(vertices.size * 6)
        vertices.forEachIndexed { index, vertex ->
            val offset = index * 6
            result[offset] = vertex[0]; result[offset + 1] = vertex[1]; result[offset + 2] = vertex[2]
            result[offset + 3] = 0.28f + (index % 3) * 0.18f
            result[offset + 4] = 0.55f
            result[offset + 5] = 0.95f
        }
        return result
    }
}
