package com.jarvis.os.app.core.voice.di

import com.jarvis.os.app.core.voice.AndroidSpeechSynthesizer
import com.jarvis.os.app.core.voice.AndroidSpeechToTextController
import com.jarvis.os.app.core.voice.SpeechSynthesizer
import com.jarvis.os.app.core.voice.SpeechToTextController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Sprint 14-16 "Voice Experience": same swap point every other pluggable concern in this codebase already has -- a premium voice provider is a new class plus a change to these two lines, not a redesign. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds
    @Singleton
    abstract fun bindSpeechSynthesizer(impl: AndroidSpeechSynthesizer): SpeechSynthesizer

    @Binds
    @Singleton
    abstract fun bindSpeechToTextController(impl: AndroidSpeechToTextController): SpeechToTextController
}
