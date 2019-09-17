package net.trivernis.chunkmaster.lib

import net.trivernis.chunkmaster.Chunkmaster
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture

class GenerationTaskSpigot(
    private val plugin: Chunkmaster, override val world: World,
    centerChunk: Chunk, private val startChunk: Chunk,
    override val stopAfter: Int = -1
) : GenerationTask(plugin, centerChunk, startChunk) {


    override var count = 0
        private set
    override var lastChunk: Chunk = startChunk
        private set
    override var endReached: Boolean = false
        private set

    /**
     * Runs the generation task. Every Iteration the next chunk will be generated if
     * it hasn't been generated already.
     * After 10 chunks have been generated, they will all be unloaded and saved.
     */
    override fun run() {
        if (plugin.mspt < msptThreshold) {    // pause when tps < 2
            if (loadedChunks.size > 10) {
                for (chunk in loadedChunks) {
                    if (chunk.isLoaded) {
                        chunk.unload(true)
                    }
                }
            } else {
                if (borderReached()) {
                    endReached = true
                    return
                }

                var chunk = nextChunk

                for (i in 1 until chunkSkips) {
                    if (world.isChunkGenerated(chunk.x, chunk.z)) {
                        chunk = nextChunk
                    } else {
                        break
                    }
                }

                if (!world.isChunkGenerated(chunk.x, chunk.z)) {
                    chunk.load(true)
                    loadedChunks.add(chunk)
                }
                lastChunk = chunk
                count = spiral.count // set the count to the more accurate spiral count
            }
        }
    }

    /**
     * Cancels the generation task.
     * This unloads all chunks that were generated but not unloaded yet.
     */
    override fun cancel() {
        for (chunk in loadedChunks) {
            if (chunk.isLoaded) {
                chunk.unload(true)
            }
        }
    }
}