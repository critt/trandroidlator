package com.critt.trandroidlator.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.critt.trandroidlator.data.ApiResult
import com.critt.trandroidlator.data.AudioSource
import com.critt.trandroidlator.data.LanguageData
import com.critt.trandroidlator.data.Speaker
import com.critt.trandroidlator.data.TranslationRepository
import com.critt.trandroidlator.data.defaultLangObject
import com.critt.trandroidlator.data.defaultLangSubject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: TranslationRepository, private val audioSource: AudioSource) :
    ViewModel() {

    val translationObject = MutableLiveData("")
    private val builderObject = StringBuilder()
    val translationSubject = MutableLiveData("")
    private val builderSubject = StringBuilder()

    var langSubject = MutableLiveData(defaultLangSubject)
    var langObject = MutableLiveData(defaultLangObject)
    val supportedLanguages: LiveData<ApiResult<List<LanguageData>>?> by lazy { repository.getSupportedLanguages().asLiveData()}

    var isConnected = MutableLiveData(false) //TODO: this makes no sense
    private var jobRecord: Job? = null
    var speakerCurr = Speaker.SUBJECT

    fun connect(): Boolean {
        if (langSubject.value == null || langObject.value == null) {
            return false
        }

        isConnected.postValue(true)  //TODO: this makes no sense
        builderSubject.clear()
        builderObject.clear()

        viewModelScope.launch(context = Dispatchers.IO) {
            repository.connectSubject(langSubject.value!!.language, langObject.value!!.language).collect {
                Timber.d("speakerCurr: $speakerCurr")
                if (it.isFinal) {
                    builderSubject.append(it.data)
                    translationSubject.postValue(builderSubject.toString())
                } else {
                    translationSubject.postValue(builderSubject.toString() + it.data)
                }
            }
        }

        viewModelScope.launch(context = Dispatchers.IO) {
            repository.connectObject(langSubject.value!!.language, langObject.value!!.language).collect {
                Timber.d("speakerCurr: $speakerCurr")
                if (it.isFinal) {
                    builderObject.append(it.data)
                    translationObject.postValue(builderObject.toString())
                } else {
                    translationObject.postValue(builderObject.toString() + it.data)
                }
            }
        }

        return true
    }

    fun startRecording() {
        if (jobRecord == null) {
            jobRecord = viewModelScope.launch(context = Dispatchers.IO) {
                audioSource.startRecording(::handleInput)
            }
        }
    }

    fun stopRecording() {
        audioSource.stopRecording()
        jobRecord?.cancel()
        jobRecord = null
    }

    fun handleInput(data: ByteArray) {
        if (speakerCurr == Speaker.SUBJECT) {
            repository.onData(data, ByteArray(2048))
        } else {
            repository.onData(ByteArray(2048), data)
        }
    }

    fun disconnect() {
        isConnected.postValue(false)//TODO: this makes no sense
        repository.disconnect()
    }
}