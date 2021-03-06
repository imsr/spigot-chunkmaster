package net.trivernis.chunkmaster.lib.generation

import net.trivernis.chunkmaster.Chunkmaster
import org.bukkit.Chunk
import org.bukkit.World
import java.util.concurrent.CompletableFuture

class GenerationTaskPaper(
    private val plugin: Chunkmaster, override val world: World,
    centerChunk: ChunkCoordinates, private val startChunk: ChunkCoordinates,
    override val stopAfter: Int = -1
) : GenerationTask(plugin, centerChunk, startChunk) {

    private val maxPendingChunks = plugin.config.getInt("generation.max-pending-chunks")

    private val pendingChunks = HashSet<CompletableFuture<Chunk>>()

    override var count = 0
        private set
    override var endReached: Boolean = false

    init {
        updateDynmapMarker()
    }

    /**
     * Runs the generation task. Every Iteration the next chunk will be generated if
     * it hasn't been generated already.
     * After 10 chunks have been generated, they will all be unloaded and saved.
     */
    override fun run() {
        if (plugin.mspt < msptThreshold) {    // pause when tps < 2
            if (loadedChunks.size > maxLoadedChunks) {
                unloadLoadedChunks()
            } else if (pendingChunks.size < maxPendingChunks) {   // if more than 10 chunks are pending, wait.
                if (borderReached()) {
                    setEndReached()
                    return
                }

                var chunk = nextChunkCoordinates
                for (i in 1 until chunkSkips) {
                    if (world.isChunkGenerated(chunk.x, chunk.z)) {
                        chunk = nextChunkCoordinates
                    } else {
                        break
                    }
                }

                if (!world.isChunkGenerated(chunk.x, chunk.z)) {
                    for (i in 0 until minOf(chunksPerStep, (stopAfter - count) - 1)) {
                        if (!world.isChunkGenerated(chunk.x, chunk.z)) {
                            pendingChunks.add(world.getChunkAtAsync(chunk.x, chunk.z, true))
                        }
                        chunk = nextChunkCoordinates
                    }
                    if (!world.isChunkGenerated(chunk.x, chunk.z)) {
                        pendingChunks.add(world.getChunkAtAsync(chunk.x, chunk.z, true))
                    }
                }
                lastChunkCoords = chunk
                count = spiral.count // set the count to the more accurate spiral count
            }
        }
        checkChunksLoaded()
    }

    /**
     * Cancels the generation task.
     * This unloads all chunks that were generated but not unloaded yet.
     */
    override fun cancel() {
        updateDynmapMarker(true)
        unloadAllChunks()
    }

    /**
     * Cancels all pending chunks and unloads all loaded chunks.
     */
    private fun unloadAllChunks() {
        for (pendingChunk in pendingChunks) {
            if (pendingChunk.isDone) {
                loadedChunks.add(pendingChunk.get())
            } else {
                pendingChunk.cancel(true)
            }
        }
        pendingChunks.clear()
        if (loadedChunks.isNotEmpty()) {
            lastChunkCoords = ChunkCoordinates(loadedChunks.last().x, loadedChunks.last().z)
        }
        for (chunk in loadedChunks) {
            if (chunk.isLoaded) {
                chunk.unload(true)
            }
        }
    }

    /**
     * Checks if some chunks have been loaded and adds them to the loaded chunk set.
     */
    private fun checkChunksLoaded() {
        val completedEntrys = HashSet<CompletableFuture<Chunk>>()
        for (pendingChunk in pendingChunks) {
            if (pendingChunk.isDone) {
                completedEntrys.add(pendingChunk)
                loadedChunks.add(pendingChunk.get())
            } else if (pendingChunk.isCompletedExceptionally || pendingChunk.isCancelled) {
                completedEntrys.add(pendingChunk)
            }
        }
        pendingChunks.removeAll(completedEntrys)
    }
}