using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using LameSounds.Core.Models;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

namespace LameSounds.Core.Audio
{
    public class AudioEngine : IDisposable
    {
        private readonly DeviceManager _deviceManager;
        private readonly MicPassthrough _micPassthrough;
        private readonly ConcurrentDictionary<string, CachedSound> _soundCache = new();
        private readonly ConcurrentDictionary<string, List<CachedSoundSampleProvider>> _activeVoices = new();

        private WasapiOut? _monitorOut;
        private WasapiOut? _broadcastOut;

        private MixingSampleProvider? _monitorMixer;
        private MixingSampleProvider? _broadcastMixer;

        private MeteringSampleProvider? _monitorMeter;
        private MeteringSampleProvider? _broadcastMeter;

        private AudioConfig _config = new();
        private bool _isDisposed;

        public event EventHandler<(float MonitorPeak, float BroadcastPeak, float MicPeak)>? VUMeterUpdated;
        public event EventHandler<string>? SoundPlaybackStarted;
        public event EventHandler<string>? SoundPlaybackEnded;

        private float _lastMonitorPeak;
        private float _lastBroadcastPeak;
        private float _lastMicPeak;

        public DeviceManager DeviceManager => _deviceManager;
        public MicPassthrough MicPassthrough => _micPassthrough;
        public AudioConfig Config => _config;

        public AudioEngine(DeviceManager deviceManager)
        {
            _deviceManager = deviceManager;
            _micPassthrough = new MicPassthrough();
            _micPassthrough.PeakLevelUpdated += (s, peak) => _lastMicPeak = peak;
        }

        public void Initialize(AudioConfig config)
        {
            _config = config;
            RestartAudioStreams();
        }

        public void RestartAudioStreams()
        {
            StopStreams();

            try
            {
                var format = CachedSound.StandardFormat;

                // 1. Setup Monitor Output (Headphones)
                _monitorMixer = new MixingSampleProvider(format) { ReadFully = true };
                _monitorMeter = new MeteringSampleProvider(_monitorMixer);
                _monitorMeter.StreamVolume += (s, args) =>
                {
                    _lastMonitorPeak = args.MaxSampleValues.Length > 0 ? Math.Max(args.MaxSampleValues[0], args.MaxSampleValues[^1]) : 0f;
                    EmitVUMeters();
                };

                var monitorDev = _deviceManager.GetDeviceById(_config.MonitorDeviceId, DataFlow.Render);
                if (monitorDev != null)
                {
                    try
                    {
                        ISampleProvider monitorSource = _monitorMeter;
                        var devFormat = monitorDev.AudioClient.MixFormat;

                        if (devFormat.SampleRate != format.SampleRate)
                        {
                            monitorSource = new WdlResamplingSampleProvider(monitorSource, devFormat.SampleRate);
                        }
                        if (devFormat.Channels == 1 && monitorSource.WaveFormat.Channels == 2)
                        {
                            monitorSource = new StereoToMonoSampleProvider(monitorSource);
                        }
                        else if (devFormat.Channels > 2)
                        {
                            var multi = new MultiplexingSampleProvider(new[] { monitorSource }, devFormat.Channels);
                            multi.ConnectInputToOutput(0, 0);
                            multi.ConnectInputToOutput(1, 1);
                            monitorSource = multi;
                        }

                        _monitorOut = new WasapiOut(monitorDev, AudioClientShareMode.Shared, false, _config.BufferLatencyMs);
                        _monitorOut.Init(monitorSource);
                        _monitorOut.Play();
                        System.Diagnostics.Debug.WriteLine($"[AudioEngine] Monitor output started on: {monitorDev.FriendlyName}");
                    }
                    catch (Exception ex)
                    {
                        System.Diagnostics.Debug.WriteLine($"[AudioEngine] Monitor output failed to init on {monitorDev.FriendlyName}: {ex.Message}");
                    }
                }

                // 2. Setup Broadcast Output (VB-CABLE or secondary output)
                if (!string.IsNullOrWhiteSpace(_config.BroadcastDeviceId))
                {
                    var broadcastDev = _deviceManager.GetDeviceById(_config.BroadcastDeviceId, DataFlow.Render);
                    if (broadcastDev != null)
                    {
                        try
                        {
                            _broadcastMixer = new MixingSampleProvider(format) { ReadFully = true };

                            // Add mic passthrough to broadcast mixer if enabled
                            if (_config.MicPassthroughEnabled)
                            {
                                _broadcastMixer.AddMixerInput(_micPassthrough);
                            }

                            _broadcastMeter = new MeteringSampleProvider(_broadcastMixer);
                            _broadcastMeter.StreamVolume += (s, args) =>
                            {
                                _lastBroadcastPeak = args.MaxSampleValues.Length > 0 ? Math.Max(args.MaxSampleValues[0], args.MaxSampleValues[^1]) : 0f;
                            };

                            ISampleProvider broadcastSource = _broadcastMeter;
                            var devFormat = broadcastDev.AudioClient.MixFormat;

                            if (devFormat.SampleRate != format.SampleRate)
                            {
                                broadcastSource = new WdlResamplingSampleProvider(broadcastSource, devFormat.SampleRate);
                            }
                            if (devFormat.Channels == 1 && broadcastSource.WaveFormat.Channels == 2)
                            {
                                broadcastSource = new StereoToMonoSampleProvider(broadcastSource);
                            }
                            else if (devFormat.Channels > 2)
                            {
                                var multi = new MultiplexingSampleProvider(new[] { broadcastSource }, devFormat.Channels);
                                multi.ConnectInputToOutput(0, 0);
                                multi.ConnectInputToOutput(1, 1);
                                broadcastSource = multi;
                            }

                            _broadcastOut = new WasapiOut(broadcastDev, AudioClientShareMode.Shared, false, _config.BufferLatencyMs);
                            _broadcastOut.Init(broadcastSource);
                            _broadcastOut.Play();
                            System.Diagnostics.Debug.WriteLine($"[AudioEngine] Broadcast output started on: {broadcastDev.FriendlyName}");
                        }
                        catch (Exception ex)
                        {
                            System.Diagnostics.Debug.WriteLine($"[AudioEngine] Broadcast output failed to init: {ex.Message}");
                        }
                    }
                }

                // 3. Setup Mic Passthrough
                if (_config.MicPassthroughEnabled)
                {
                    var micDev = _deviceManager.GetDeviceById(_config.MicDeviceId, DataFlow.Capture);
                    _micPassthrough.Volume = _config.MicVolume;
                    _micPassthrough.Start(micDev);
                }
                else
                {
                    _micPassthrough.Stop();
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[AudioEngine] RestartStreams error: {ex.Message}");
            }
        }

        private void EmitVUMeters()
        {
            VUMeterUpdated?.Invoke(this, (_lastMonitorPeak, _lastBroadcastPeak, _lastMicPeak));
        }

        public void LoadSound(SoundClip clip)
        {
            if (string.IsNullOrWhiteSpace(clip.FilePath) || !File.Exists(clip.FilePath))
            {
                // Fallback synth tone for testing if file does not exist yet
                var synth = CachedSound.CreateSynthTone(440.0 + (clip.Title.GetHashCode() % 400), 0.6);
                _soundCache[clip.Id] = synth;
                return;
            }

            try
            {
                var cached = new CachedSound(clip.FilePath);
                _soundCache[clip.Id] = cached;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[AudioEngine] LoadSound '{clip.FilePath}' failed: {ex.Message}");
                _soundCache[clip.Id] = CachedSound.CreateSynthTone(300.0, 0.4);
            }
        }

        public void UnloadSound(string soundId)
        {
            _soundCache.TryRemove(soundId, out _);
        }

        public void PlaySound(string soundId, float? volumeOverride = null)
        {
            if (!_soundCache.TryGetValue(soundId, out var cachedSound))
            {
                // Sound not cached yet, try synth tone
                cachedSound = CachedSound.CreateSynthTone(550.0, 0.5);
                _soundCache[soundId] = cachedSound;
            }

            float baseVolume = volumeOverride ?? 1.0f;
            float monitorVol = baseVolume * _config.MonitorVolume;
            float broadcastVol = baseVolume * _config.BroadcastVolume;

            var monitorVoice = new CachedSoundSampleProvider(cachedSound, monitorVol);
            var broadcastVoice = new CachedSoundSampleProvider(cachedSound, broadcastVol);

            var voiceList = _activeVoices.GetOrAdd(soundId, _ => new List<CachedSoundSampleProvider>());
            lock (voiceList)
            {
                voiceList.Add(monitorVoice);
                voiceList.Add(broadcastVoice);
            }

            // Auto-ducking: dip mic volume while sound plays
            if (_config.AutoDuckingEnabled && _micPassthrough.IsCapturing)
            {
                _micPassthrough.DuckingMultiplier = 1.0f - _config.DuckingReductionPercent;
            }

            void HandleVoiceEnded(CachedSoundSampleProvider voice)
            {
                bool isEmpty = false;
                lock (voiceList)
                {
                    voiceList.Remove(voice);
                    isEmpty = voiceList.Count == 0;
                }

                if (isEmpty)
                {
                    if (_activeVoices.Values.All(list => { lock (list) return list.Count == 0; }))
                    {
                        // Restore ducking
                        _micPassthrough.DuckingMultiplier = 1.0f;
                    }

                    SoundPlaybackEnded?.Invoke(this, soundId);
                }
            }

            monitorVoice.PlaybackEnded += (s, e) => HandleVoiceEnded(monitorVoice);
            broadcastVoice.PlaybackEnded += (s, e) => HandleVoiceEnded(broadcastVoice);

            _monitorMixer?.AddMixerInput(monitorVoice);
            _broadcastMixer?.AddMixerInput(broadcastVoice);

            SoundPlaybackStarted?.Invoke(this, soundId);
        }

        public void StopSound(string soundId)
        {
            if (_activeVoices.TryGetValue(soundId, out var list))
            {
                lock (list)
                {
                    foreach (var voice in list.ToArray())
                    {
                        voice.Stop();
                    }
                    list.Clear();
                }
            }

            _micPassthrough.DuckingMultiplier = 1.0f;
            SoundPlaybackEnded?.Invoke(this, soundId);
        }

        public void StopAll()
        {
            foreach (var kvp in _activeVoices)
            {
                lock (kvp.Value)
                {
                    foreach (var voice in kvp.Value.ToArray())
                    {
                        voice.Stop();
                    }
                    kvp.Value.Clear();
                }
            }

            _micPassthrough.DuckingMultiplier = 1.0f;
        }

        public void UpdateVolumes(float monitorVol, float broadcastVol, float micVol)
        {
            _config.MonitorVolume = monitorVol;
            _config.BroadcastVolume = broadcastVol;
            _config.MicVolume = micVol;
            _micPassthrough.Volume = micVol;
        }

        public void UpdateDevices(string? monitorId, string? broadcastId, string? micId, bool micPassthroughEnabled)
        {
            _config.MonitorDeviceId = monitorId ?? string.Empty;
            _config.BroadcastDeviceId = broadcastId ?? string.Empty;
            _config.MicDeviceId = micId ?? string.Empty;
            _config.MicPassthroughEnabled = micPassthroughEnabled;

            RestartAudioStreams();
        }

        private void StopStreams()
        {
            _micPassthrough.Stop();

            if (_monitorOut != null)
            {
                try
                {
                    _monitorOut.Stop();
                    _monitorOut.Dispose();
                }
                catch { }
                _monitorOut = null;
            }

            if (_broadcastOut != null)
            {
                try
                {
                    _broadcastOut.Stop();
                    _broadcastOut.Dispose();
                }
                catch { }
                _broadcastOut = null;
            }

            _monitorMixer?.RemoveAllMixerInputs();
            _broadcastMixer?.RemoveAllMixerInputs();
            _monitorMixer = null;
            _broadcastMixer = null;
        }

        public void Dispose()
        {
            if (_isDisposed) return;
            _isDisposed = true;

            StopAll();
            StopStreams();
            _micPassthrough.Dispose();
        }
    }
}
