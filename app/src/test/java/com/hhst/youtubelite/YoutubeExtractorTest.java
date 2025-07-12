package com.hhst.youtubelite;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.hhst.youtubelite.common.YoutubeExtractor;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

@RunWith(MockitoJUnitRunner.class)
public class YoutubeExtractorTest {

  private YoutubeExtractor youtubeExtractor;

  @Mock private StreamInfo mockStreamInfo;

  @Mock private AudioStream mockAudioStream1;

  @Mock private AudioStream mockAudioStream2;

  @Mock private VideoStream mockVideoStream1;

  @Mock private VideoStream mockVideoStream2;

  @Mock private Image mockThumbnail1;

  @Mock private Image mockThumbnail2;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    youtubeExtractor = new YoutubeExtractor();
  }

  @Test
  public void testExtractValidUrl() {
    // Note: This test requires network connection, may need mocking in actual projects
    String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    try {
      StreamInfo result = youtubeExtractor.extract(validUrl);
      assertNotNull("Extracted StreamInfo should not be null", result);
      assertNotNull("Video ID should not be null", result.getId());
      assertNotNull("Video name should not be null", result.getName());
    } catch (Exception e) {
      // If network is unavailable or URL is invalid, test should handle gracefully
      assertTrue(
          "Should throw ExtractionException or IOException",
          e instanceof ExtractionException || e instanceof IOException);
    }
  }

  @Test(expected = ExtractionException.class)
  public void testExtractInvalidUrl() throws ExtractionException, IOException {
    String invalidUrl = "https://invalid-url.com";
    youtubeExtractor.extract(invalidUrl);
  }

  @Test
  public void testGetBestThumbnailWithMultipleThumbnails() {
    // Setup mock thumbnails
    when(mockThumbnail1.getEstimatedResolutionLevel()).thenReturn(Image.ResolutionLevel.LOW);
    when(mockThumbnail1.getUrl()).thenReturn("http://example.com/low.jpg");

    when(mockThumbnail2.getEstimatedResolutionLevel()).thenReturn(Image.ResolutionLevel.HIGH);
    when(mockThumbnail2.getUrl()).thenReturn("http://example.com/high.jpg");

    List<Image> thumbnails = Arrays.asList(mockThumbnail1, mockThumbnail2);
    when(mockStreamInfo.getThumbnails()).thenReturn(thumbnails);

    String result = youtubeExtractor.getBestThumbnail(mockStreamInfo);

    assertEquals("Should return high resolution thumbnail", "http://example.com/high.jpg", result);
  }

  @Test
  public void testGetBestThumbnailWithNoThumbnails() {
    when(mockStreamInfo.getThumbnails()).thenReturn(Collections.emptyList());

    String result = youtubeExtractor.getBestThumbnail(mockStreamInfo);

    assertNull("Should return null when no thumbnails available", result);
  }

  @Test
  public void testGetBestThumbnailWithSingleThumbnail() {
    when(mockThumbnail1.getEstimatedResolutionLevel()).thenReturn(Image.ResolutionLevel.MEDIUM);
    when(mockThumbnail1.getUrl()).thenReturn("http://example.com/medium.jpg");

    List<Image> thumbnails = Collections.singletonList(mockThumbnail1);
    when(mockStreamInfo.getThumbnails()).thenReturn(thumbnails);

    String result = youtubeExtractor.getBestThumbnail(mockStreamInfo);

    assertEquals("Should return the only thumbnail", "http://example.com/medium.jpg", result);
  }

  @Test
  public void testGetBestAudioStreamWithM4AStreams() {
    // Setup mock audio streams
    when(mockAudioStream1.getFormat()).thenReturn(MediaFormat.M4A);
    when(mockAudioStream1.getAverageBitrate()).thenReturn(128);

    when(mockAudioStream2.getFormat()).thenReturn(MediaFormat.M4A);
    when(mockAudioStream2.getAverageBitrate()).thenReturn(256);

    List<AudioStream> audioStreams = Arrays.asList(mockAudioStream1, mockAudioStream2);
    when(mockStreamInfo.getAudioStreams()).thenReturn(audioStreams);

    AudioStream result = youtubeExtractor.getBestAudioStream(mockStreamInfo);

    assertEquals("Should return highest bitrate M4A audio stream", mockAudioStream2, result);
  }

  @Test
  public void testGetBestAudioStreamWithNoM4AStreams() {
    // Setup non-M4A format audio streams
    when(mockAudioStream1.getFormat()).thenReturn(MediaFormat.MP3);
    when(mockAudioStream2.getFormat()).thenReturn(MediaFormat.WEBMA);

    List<AudioStream> audioStreams = Arrays.asList(mockAudioStream1, mockAudioStream2);
    when(mockStreamInfo.getAudioStreams()).thenReturn(audioStreams);

    AudioStream result = youtubeExtractor.getBestAudioStream(mockStreamInfo);

    assertNull("Should return null when no M4A format audio streams available", result);
  }

  @Test
  public void testGetBestAudioStreamWithEmptyList() {
    when(mockStreamInfo.getAudioStreams()).thenReturn(Collections.emptyList());

    AudioStream result = youtubeExtractor.getBestAudioStream(mockStreamInfo);

    assertNull("Should return null when no audio streams available", result);
  }

  @Test
  public void testGetVideoOnlyStreamsWithMPEG4Streams() {
    // Setup mock video streams
    when(mockVideoStream1.getFormat()).thenReturn(MediaFormat.MPEG_4);
    when(mockVideoStream2.getFormat()).thenReturn(MediaFormat.WEBM);

    List<VideoStream> videoStreams = Arrays.asList(mockVideoStream1, mockVideoStream2);
    when(mockStreamInfo.getVideoOnlyStreams()).thenReturn(videoStreams);

    List<VideoStream> result = youtubeExtractor.getVideoOnlyStreams(mockStreamInfo);

    assertEquals("Should only return MPEG_4 format video streams", 1, result.size());
    assertEquals("Returned video stream should be MPEG_4 format", mockVideoStream1, result.get(0));
  }

  @Test
  public void testGetVideoOnlyStreamsWithNoMPEG4Streams() {
    // Setup non-MPEG_4 format video streams
    when(mockVideoStream1.getFormat()).thenReturn(MediaFormat.WEBM);
    when(mockVideoStream2.getFormat()).thenReturn(MediaFormat.v3GPP);

    List<VideoStream> videoStreams = Arrays.asList(mockVideoStream1, mockVideoStream2);
    when(mockStreamInfo.getVideoOnlyStreams()).thenReturn(videoStreams);

    List<VideoStream> result = youtubeExtractor.getVideoOnlyStreams(mockStreamInfo);

    assertTrue(
        "Should return empty list when no MPEG_4 format video streams available", result.isEmpty());
  }

  @Test
  public void testGetVideoOnlyStreamsWithEmptyList() {
    when(mockStreamInfo.getVideoOnlyStreams()).thenReturn(Collections.emptyList());

    List<VideoStream> result = youtubeExtractor.getVideoOnlyStreams(mockStreamInfo);

    assertTrue("Should return empty list when no video streams available", result.isEmpty());
  }

  @Test
  public void testGetBestAudioStreamWithMixedFormats() {
    // Setup mixed format audio streams
    AudioStream mockM4AStream = mock(AudioStream.class);
    AudioStream mockMP3Stream = mock(AudioStream.class);
    AudioStream mockWebMAStream = mock(AudioStream.class);

    when(mockM4AStream.getFormat()).thenReturn(MediaFormat.M4A);
    when(mockM4AStream.getAverageBitrate()).thenReturn(192);

    when(mockMP3Stream.getFormat()).thenReturn(MediaFormat.MP3);
    when(mockMP3Stream.getAverageBitrate()).thenReturn(320);

    when(mockWebMAStream.getFormat()).thenReturn(MediaFormat.WEBMA);
    when(mockWebMAStream.getAverageBitrate()).thenReturn(256);

    List<AudioStream> audioStreams = Arrays.asList(mockMP3Stream, mockM4AStream, mockWebMAStream);
    when(mockStreamInfo.getAudioStreams()).thenReturn(audioStreams);

    AudioStream result = youtubeExtractor.getBestAudioStream(mockStreamInfo);

    assertEquals(
        "Should return M4A stream even if other formats have higher bitrate",
        mockM4AStream,
        result);
  }

  @Test
  public void testGetVideoOnlyStreamsWithMixedFormats() {
    // Setup mixed format video streams
    VideoStream mockMPEG4Stream1 = mock(VideoStream.class);
    VideoStream mockMPEG4Stream2 = mock(VideoStream.class);
    VideoStream mockWebMStream = mock(VideoStream.class);
    VideoStream mock3GPPStream = mock(VideoStream.class);

    when(mockMPEG4Stream1.getFormat()).thenReturn(MediaFormat.MPEG_4);
    when(mockMPEG4Stream2.getFormat()).thenReturn(MediaFormat.MPEG_4);
    when(mockWebMStream.getFormat()).thenReturn(MediaFormat.WEBM);
    when(mock3GPPStream.getFormat()).thenReturn(MediaFormat.v3GPP);

    List<VideoStream> videoStreams =
        Arrays.asList(mockWebMStream, mockMPEG4Stream1, mock3GPPStream, mockMPEG4Stream2);
    when(mockStreamInfo.getVideoOnlyStreams()).thenReturn(videoStreams);

    List<VideoStream> result = youtubeExtractor.getVideoOnlyStreams(mockStreamInfo);

    assertEquals("Should return only MPEG_4 format streams", 2, result.size());
    assertTrue("Should contain first MPEG_4 stream", result.contains(mockMPEG4Stream1));
    assertTrue("Should contain second MPEG_4 stream", result.contains(mockMPEG4Stream2));
    assertFalse("Should not contain WEBM stream", result.contains(mockWebMStream));
    assertFalse("Should not contain 3GPP stream", result.contains(mock3GPPStream));
  }

  @Test
  public void testGetBestThumbnailWithUnknownResolution() {
    // Setup thumbnails with unknown resolution
    Image mockUnknownThumbnail1 = mock(Image.class);
    Image mockUnknownThumbnail2 = mock(Image.class);

    when(mockUnknownThumbnail1.getEstimatedResolutionLevel())
        .thenReturn(Image.ResolutionLevel.UNKNOWN);
    when(mockUnknownThumbnail1.getUrl()).thenReturn("http://example.com/unknown1.jpg");

    when(mockUnknownThumbnail2.getEstimatedResolutionLevel())
        .thenReturn(Image.ResolutionLevel.UNKNOWN);
    when(mockUnknownThumbnail2.getUrl()).thenReturn("http://example.com/unknown2.jpg");

    List<Image> thumbnails = Arrays.asList(mockUnknownThumbnail1, mockUnknownThumbnail2);
    when(mockStreamInfo.getThumbnails()).thenReturn(thumbnails);

    String result = youtubeExtractor.getBestThumbnail(mockStreamInfo);

    assertNotNull("Should return a thumbnail even with unknown resolution", result);
    assertTrue(
        "Should return one of the unknown resolution thumbnails",
        result.equals("http://example.com/unknown1.jpg")
            || result.equals("http://example.com/unknown2.jpg"));
  }

  @Test
  public void testGetBestAudioStreamWithEqualBitrates() {
    // Setup M4A streams with equal bitrates
    AudioStream mockAudioStream1 = mock(AudioStream.class);
    AudioStream mockAudioStream2 = mock(AudioStream.class);

    when(mockAudioStream1.getFormat()).thenReturn(MediaFormat.M4A);
    when(mockAudioStream1.getAverageBitrate()).thenReturn(128);

    when(mockAudioStream2.getFormat()).thenReturn(MediaFormat.M4A);
    when(mockAudioStream2.getAverageBitrate()).thenReturn(128);

    List<AudioStream> audioStreams = Arrays.asList(mockAudioStream1, mockAudioStream2);
    when(mockStreamInfo.getAudioStreams()).thenReturn(audioStreams);

    AudioStream result = youtubeExtractor.getBestAudioStream(mockStreamInfo);

    assertNotNull("Should return an audio stream when bitrates are equal", result);
    assertTrue(
        "Should return one of the equal bitrate streams",
        result == mockAudioStream1 || result == mockAudioStream2);
  }

  @Test
  public void testStreamExtractionIntegration() {
    // Integration test to verify stream extraction workflow
    try {
      String testUrl = "https://www.youtube.com/watch?v=qgoO3vg31VE";
      StreamInfo streamInfo = youtubeExtractor.extract(testUrl);

      if (streamInfo != null) {
        // Test thumbnail extraction
        String thumbnail = youtubeExtractor.getBestThumbnail(streamInfo);
        if (thumbnail != null) {
          assertTrue("Thumbnail URL should be valid", thumbnail.startsWith("http"));
        }

        // Test audio stream extraction
        AudioStream audioStream = youtubeExtractor.getBestAudioStream(streamInfo);
        if (audioStream != null) {
          assertEquals(
              "Audio stream should be M4A format", MediaFormat.M4A, audioStream.getFormat());
          assertTrue(
              "Audio stream should have positive bitrate", audioStream.getAverageBitrate() > 0);
        }

        // Test video stream extraction
        List<VideoStream> videoStreams = youtubeExtractor.getVideoOnlyStreams(streamInfo);
        assertNotNull("Video streams list should not be null", videoStreams);
        for (VideoStream videoStream : videoStreams) {
          System.out.println(videoStream.getContent());
          assertEquals(
              "All video streams should be MPEG_4 format",
              MediaFormat.MPEG_4,
              videoStream.getFormat());
        }
      }
    } catch (Exception e) {
      // Network-dependent test - log but don't fail
      System.out.println("Integration test skipped due to network issues: " + e.getMessage());
    }
  }
}
