package dev.dreamplayer.data

import dev.dreamplayer.core.TrackMeta
import dev.dreamplayer.model.Track
import dev.dreamplayer.model.TrackColor

internal fun TrackMeta.toTrack() = Track(
    id = id,
    path = path,
    title = title.ifEmpty { path?.substringAfterLast('/') ?: "(без названия)" },
    artists = artists,
    album = album,
    genres = genres,
    durationS = durationS,
    sampleRate = sampleRate,
    bitrate = bitrate,
    listenCount = listenCount,
    coverArt = coverArt,
    volume = volume,
    invalid = invalid,
    color = TrackColor.of(color),
    label = label,
)
