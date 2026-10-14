using System;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

namespace LameSounds.Core.Audio
{
    /// <summary>
    /// Captures physical microphone input and feeds it into an ISampleProvider
    /// resampled to 48kHz stereo float format with dynamic ducking.
    /// </summary>
    public class MicPassthrough : ISampleProvider, IDisposable
    {
        private WasapiCapture? _capture;
        private BufferedWaveProvider? _bufferedWaveProvider;
        private ISampleProvider? _sampleProvider;
        private readonly WaveFormat _outputFormat = CachedSound.StandardFormat;

        private float _volume = 1.0f;
        private float _duckingMultiplier = 1.0f;
        private bool _isCapturing = false;
        private readonly object _lock = new();

        public WaveFormat WaveFormat => _outputFormat;

        public float Volume
        {
            get => _volume;
            set => _volume = Math.Clamp(value, 0f, 2f);
        }

        public float DuckingMultiplier
        {
            get => _duckingMultiplier;
            set => _duckingMultiplier = Math.Clamp(value, 0f, 1f);
        }

        public bool IsCapturing => _isCapturing;

        public event EventHandler<float>? PeakLevelUpdated;

        public void Start(MMDevice? micDevice)
        {
            Stop();

            lock (_lock)
            {
                try
                {
                    _capture = micDevice != null ? new WasapiCapture(micDevice) : new WasapiCapture();
                    var captureFormat = _capture.WaveFormat;

                    _bufferedWaveProvider = new BufferedWaveProvider(captureFormat)
                    {
                        DiscardOnBufferOverflow = true,
                        BufferDuration = TimeSpan.FromMilliseconds(200)
                    };

                    _capture.DataAvailable += (s, a) =>
                    {
                        _bufferedWaveProvider.AddSamples(a.Buffer, 0, a.BytesRecorded);
                    };

                    // Chain conversion: ToSampleProvider -> MonoToStereo (if needed) -> Resample (if needed)
                    ISampleProvider provider = _bufferedWaveProvider.ToSampleProvider();

                    if (provider.WaveFormat.Channels == 1 && _outputFormat.Channels == 2)
                    {
                        provider = new MonoToStereoSampleProvider(provider);
                    }

                    if (provider.WaveFormat.SampleRate != _outputFormat.SampleRate)
                    {
                        provider = new WdlResamplingSampleProvider(provider, _outputFormat.SampleRate);
                    }

                    _sampleProvider = provider;
                    _capture.StartRecording();
                    _isCapturing = true;
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[MicPassthrough] Start error: {ex.Message}");
                    _isCapturing = false;
                }
            }
        }

        public void Stop()
        {
            lock (_lock)
            {
                if (_capture != null)
                {
                    try
                    {
                        _capture.StopRecording();
                        _capture.Dispose();
                    }
                    catch { }
                    _capture = null;
                }

                _bufferedWaveProvider?.ClearBuffer();
                _bufferedWaveProvider = null;
                _sampleProvider = null;
                _isCapturing = false;
            }
        }

        public int Read(float[] buffer, int offset, int count)
        {
            if (!_isCapturing || _sampleProvider == null)
            {
                Array.Clear(buffer, offset, count);
                return count;
            }

            int samplesRead = _sampleProvider.Read(buffer, offset, count);
            float maxPeak = 0.0f;
            float totalGain = _volume * _duckingMultiplier;

            for (int i = 0; i < samplesRead; i++)
            {
                buffer[offset + i] *= totalGain;
                float abs = Math.Abs(buffer[offset + i]);
                if (abs > maxPeak) maxPeak = abs;
            }

            // Pad remainder with silence if buffer underflow
            if (samplesRead < count)
            {
                Array.Clear(buffer, offset + samplesRead, count - samplesRead);
            }

            PeakLevelUpdated?.Invoke(this, maxPeak);
            return count;
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
