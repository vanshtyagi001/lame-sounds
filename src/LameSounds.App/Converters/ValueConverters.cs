using System;
using System.Globalization;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using LameSounds.Core.Models;

namespace LameSounds.App.Converters
{
    public class HexToBrushConverter : IValueConverter
    {
        public static readonly HexToBrushConverter Instance = new();

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            if (value is string hex && !string.IsNullOrWhiteSpace(hex))
            {
                try
                {
                    var color = (Color)ColorConverter.ConvertFromString(hex);
                    return new SolidColorBrush(color);
                }
                catch { }
            }

            return new SolidColorBrush(Color.FromRgb(0x1F, 0x1F, 0x1F));
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class BoolToVisibilityConverter : IValueConverter
    {
        public static readonly BoolToVisibilityConverter Instance = new();
        public bool Invert { get; set; }

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            bool b = value is bool flag && flag;
            if (Invert) b = !b;
            return b ? Visibility.Visible : Visibility.Collapsed;
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class EmptySlotToVisibilityConverter : IValueConverter
    {
        public static readonly EmptySlotToVisibilityConverter Instance = new();
        public bool Invert { get; set; }

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            bool isEmpty = false;
            if (value is SoundButtonConfig cfg)
            {
                isEmpty = cfg.IsEmpty;
            }
            else if (value is bool b)
            {
                isEmpty = b;
            }

            if (Invert) isEmpty = !isEmpty;
            return isEmpty ? Visibility.Visible : Visibility.Collapsed;
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class ShapeToCornerRadiusConverter : IValueConverter
    {
        public static readonly ShapeToCornerRadiusConverter Instance = new();

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            if (value is ButtonShape shape)
            {
                return shape switch
                {
                    ButtonShape.CIRCLE => new CornerRadius(999),
                    ButtonShape.RECTANGLE => new CornerRadius(4),
                    ButtonShape.HEXAGON => new CornerRadius(12),
                    _ => new CornerRadius(16)
                };
            }
            return new CornerRadius(16);
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class IsHexagonVisibilityConverter : IValueConverter
    {
        public static readonly IsHexagonVisibilityConverter Instance = new();
        public bool Invert { get; set; }

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            bool isHex = value is ButtonShape shape && shape == ButtonShape.HEXAGON;
            if (Invert) isHex = !isHex;
            return isHex ? Visibility.Visible : Visibility.Collapsed;
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class SoundClipToBrushConverter : IValueConverter
    {
        public static readonly SoundClipToBrushConverter Instance = new();

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            if (value is SoundClip clip)
            {
                return GetBrush(clip.ColorHex, clip.GradientHex);
            }
            return new SolidColorBrush(Color.FromRgb(0x60, 0xCD, 0xFF));
        }

        public static Brush GetBrush(string? primaryHex, string? secondaryHex)
        {
            Color c1 = Color.FromRgb(0x60, 0xCD, 0xFF);
            if (!string.IsNullOrWhiteSpace(primaryHex))
            {
                try { c1 = (Color)ColorConverter.ConvertFromString(primaryHex); } catch { }
            }

            if (!string.IsNullOrWhiteSpace(secondaryHex))
            {
                try
                {
                    Color c2 = (Color)ColorConverter.ConvertFromString(secondaryHex);
                    return new LinearGradientBrush(c1, c2, new Point(0, 0), new Point(1, 1));
                }
                catch { }
            }

            return new SolidColorBrush(c1);
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class NullOrEmptyToVisibilityConverter : IValueConverter
    {
        public static readonly NullOrEmptyToVisibilityConverter Instance = new();
        public bool Invert { get; set; }

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            bool hasValue = value switch
            {
                null => false,
                string s => !string.IsNullOrWhiteSpace(s),
                _ => true
            };

            if (Invert) hasValue = !hasValue;
            return hasValue ? Visibility.Visible : Visibility.Collapsed;
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class EmptyCountToVisibilityConverter : IValueConverter
    {
        public static readonly EmptyCountToVisibilityConverter Instance = new();
        public bool Invert { get; set; }

        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            int count = value switch
            {
                int i => i,
                System.Collections.ICollection c => c.Count,
                _ => 0
            };

            bool isEmpty = count == 0;
            if (Invert) isEmpty = !isEmpty;
            return isEmpty ? Visibility.Visible : Visibility.Collapsed;
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }
}
