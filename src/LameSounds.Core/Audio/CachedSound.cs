using System;
using System.Collections.Generic;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

namespace LameSounds.Core.Audio
{
    /// <summary>
    /// Holds pre-decoded IEEE float audio samples in memory for zero-latency instant playback.
    /// Resampled to standard 48,000 Hz stereo format.
    /// </summary>
    public class CachedSound
    {
        public static readonly WaveFormat StandardFormat = WaveFormat.CreateIeeeFloatWaveFormat(48000, 2);

        public float[] AudioData { get; }
        public WaveFormat WaveFormat { get; }
        public TimeSpan Duration { get; }

        public CachedSound(string audioFilePath)
        {
            using var reader = new AudioFileReader(audioFilePath);
            
            // Resample to 48kHz stereo float
            ISampleProvider provider = reader;
            if (provider.WaveFormat.SampleRate != StandardFormat.SampleRate ||
                provider.WaveFormat.Channels != StandardFormat.Channels)
            {
                if (provider.WaveFormat.Channels == 1 && StandardFormat.Channels == 2)
                {
                    provider = new MonoToStereoSampleProvider(provider);
                }
                else if (provider.WaveFormat.Channels > 2)
                {
                    var multi = new MultiplexingSampleProvider(new[] { provider }, 2);
                    multi.ConnectInputToOutput(0, 0);
                    multi.ConnectInputToOutput(1, 1);
                    provider = multi;
                }

                if (provider.WaveFormat.SampleRate != StandardFormat.SampleRate)
                {
                    provider = new WdlResamplingSampleProvider(provider, StandardFormat.SampleRate);
                }
            }

            WaveFormat = StandardFormat;
            var wholeFile = new List<float>((int)(reader.Length / 4));
            var readBuffer = new float[StandardFormat.SampleRate * StandardFormat.Channels];
            int samplesRead;
            while ((samplesRead = provider.Read(readBuffer, 0, readBuffer.Length)) > 0)
            {
                for (int i = 0; i < samplesRead; i++)
                {
                    wholeFile.Add(readBuffer[i]);
                }
            }

            AudioData = wholeFile.ToArray();
            Duration = TimeSpan.FromSeconds((double)AudioData.Length / (StandardFormat.SampleRate * StandardFormat.Channels));
        }

        public CachedSound(float[] samples, TimeSpan duration)
        {
            AudioData = samples;
            WaveFormat = StandardFormat;
            Duration = duration;
        }

        /// <summary>
        /// Generates a synthetic pleasant beep sound for immediate testing.
        /// </summary>
        public static CachedSound CreateSynthTone(double frequency = 440.0, double durationSeconds = 0.5)
        {
            int sampleRate = StandardFormat.SampleRate;
            int totalSamples = (int)(sampleRate * durationSeconds * StandardFormat.Channels);
            var samples = new float[totalSamples];

            for (int i = 0; i < totalSamples / 2; i++)
            {
                double t = (double)i / sampleRate;
                // Add soft envelope attack/release
                double envelope = 1.0;
                double attackTime = 0.02;
                double releaseTime = 0.08;
                if (t < attackTime) envelope = t / attackTime;
                else if (t > durationSeconds - releaseTime) envelope = Math.Max(0.0, (durationSeconds - t) / releaseTime);

                float sample = (float)(Math.Sin(2.0 * Math.PI * frequency * t) * envelope * 0.4);
                samples[i * 2] = sample;     // Left
                samples[i * 2 + 1] = sample; // Right
            }

            return new CachedSound(samples, TimeSpan.FromSeconds(durationSeconds));
        }
    }

    /// <summary>
    /// ISampleProvider stream reading from an in-memory CachedSound.
    /// </summary>
    public class CachedSoundSampleProvider : ISampleProvider
    {
        private readonly CachedSound _cachedSound;
        private long _position;
        private float _volume = 1.0f;
        private bool _ended;
        private readonly object _lock = new();

        public event EventHandler? PlaybackEnded;

        public WaveFormat WaveFormat => _cachedSound.WaveFormat;

        public float Volume
        {
            get => _volume;
            set => _volume = Math.Clamp(value, 0f, 2f);
        }

        public CachedSoundSampleProvider(CachedSound cachedSound, float volume = 1.0f)
        {
            _cachedSound = cachedSound;
            _volume = volume;
        }

        public int Read(float[] buffer, int offset, int count)
        {
            long availableSamples = _cachedSound.AudioData.Length - _position;
            if (availableSamples <= 0)
            {
                TriggerEnded();
                return 0;
            }

            int samplesToCopy = (int)Math.Min(availableSamples, count);

            for (int i = 0; i < samplesToCopy; i++)
            {
                float s = _cachedSound.AudioData[_position + i] * _volume;
                // Anti-clipping soft limiter prevents digital crackle
                buffer[offset + i] = Math.Clamp(s, -1.0f, 1.0f);
            }

            _position += samplesToCopy;

            if (_position >= _cachedSound.AudioData.Length)
            {
                TriggerEnded();
            }

            return samplesToCopy;
        }

        private void TriggerEnded()
        {
            lock (_lock)
            {
                if (!_ended)
                {
                    _ended = true;
                    PlaybackEnded?.Invoke(this, EventArgs.Empty);
                }
            }
        }

        public void Stop()
        {
            _position = _cachedSound.AudioData.Length;
            TriggerEnded();
        }
    }
}
