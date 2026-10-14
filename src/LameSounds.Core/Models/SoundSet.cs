using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace LameSounds.Core.Models
{
    public enum ButtonShape
    {
        RECTANGLE,
        ROUNDED_RECTANGLE,
        CIRCLE,
        HEXAGON
    }

    public class SoundButtonConfig
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = Guid.NewGuid().ToString();

        [JsonPropertyName("soundId")]
        public string SoundId { get; set; } = string.Empty;

        [JsonPropertyName("label")]
        public string Label { get; set; } = "Sound";

        [JsonPropertyName("colorHex")]
        public string ColorHex { get; set; } = "#6366F1";

        [JsonPropertyName("gradientHex")]
        public string? GradientHex { get; set; }

        [JsonPropertyName("shape")]
        [JsonConverter(typeof(JsonStringEnumConverter))]
        public ButtonShape Shape { get; set; } = ButtonShape.ROUNDED_RECTANGLE;

        [JsonPropertyName("cornerRadius")]
        public int CornerRadius { get; set; } = 16;

        [JsonPropertyName("fontSizeSp")]
        public int FontSizeSp { get; set; } = 13;

        [JsonPropertyName("isBold")]
        public bool IsBold { get; set; } = true;

        [JsonPropertyName("textColorHex")]
        public string TextColorHex { get; set; } = "#FFFFFF";

        [JsonPropertyName("row")]
        public int Row { get; set; } = 0;

        [JsonPropertyName("column")]
        public int Column { get; set; } = 0;

        [JsonIgnore]
        public bool IsEmpty => string.IsNullOrWhiteSpace(SoundId) || Label.StartsWith("+ Slot", StringComparison.OrdinalIgnoreCase);
    }

    public class SoundSet
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = Guid.NewGuid().ToString();

        [JsonPropertyName("name")]
        public string Name { get; set; } = "Sound Set";

        [JsonPropertyName("rows")]
        public int Rows { get; set; } = 3;

        [JsonPropertyName("columns")]
        public int Columns { get; set; } = 3;

        [JsonPropertyName("buttons")]
        public List<SoundButtonConfig> Buttons { get; set; } = new();
    }
}
