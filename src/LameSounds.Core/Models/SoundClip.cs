using System;
using System.Text.Json.Serialization;

namespace LameSounds.Core.Models
{
    public class SoundClip
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = Guid.NewGuid().ToString();

        [JsonPropertyName("title")]
        public string Title { get; set; } = string.Empty;

        [JsonPropertyName("fileName")]
        public string FileName { get; set; } = string.Empty;

        [JsonPropertyName("filePath")]
        public string FilePath { get; set; } = string.Empty;

        [JsonPropertyName("hotkey")]
        public string Hotkey { get; set; } = string.Empty;

        [JsonPropertyName("volume")]
        public float Volume { get; set; } = 1.0f;

        [JsonPropertyName("colorHex")]
        public string ColorHex { get; set; } = "#6366F1";

        [JsonPropertyName("gradientHex")]
        public string? GradientHex { get; set; }

        [JsonPropertyName("imagePath")]
        public string? ImagePath { get; set; }

        [JsonPropertyName("hasImage")]
        public bool HasImage => !string.IsNullOrEmpty(ImagePath) && System.IO.File.Exists(ImagePath);

        [JsonPropertyName("icon")]
        public string Icon { get; set; } = "volume-high";

        [JsonPropertyName("durationSeconds")]
        public double DurationSeconds { get; set; } = 0.0;

        [JsonPropertyName("category")]
        public string Category { get; set; } = "General";

        [JsonPropertyName("fileSizeBytes")]
        public long FileSizeBytes { get; set; } = 0;

        [JsonPropertyName("sortOrder")]
        public int SortOrder { get; set; } = 0;
    }
}
