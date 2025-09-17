package com.example.lanes.api;

import com.example.lanes.config.PolygonConfig;
import com.example.lanes.core.LanePolygonService;
import com.example.lanes.core.OverlayResult;
import com.example.lanes.model.DebugFrames;
import com.example.lanes.model.PolygonDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PolygonController.class)
public class PolygonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LanePolygonService lanePolygonService;

    @MockBean
    private PolygonConfig polygonConfig;

    @Test
    public void testOverlayEndpointWithPngFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "data", "test.png", MediaType.IMAGE_PNG_VALUE, "mock image content".getBytes()
        );

        OverlayResult mockResult = new OverlayResult(
            Collections.emptyList(), 
            "mock overlay image".getBytes()
        );

        when(polygonConfig.getPpm()).thenReturn(5.0);
        when(lanePolygonService.process(any(byte[].class), anyDouble())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/polygons/overlay")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    public void testPolygonsEndpointWithPngFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "data", "test.png", MediaType.IMAGE_PNG_VALUE, "mock image content".getBytes()
        );

        List<PolygonDto> mockPolygons = Collections.singletonList(
            new PolygonDto("test", Collections.emptyList(), 100.0, Collections.emptyMap(), Collections.emptyList())
        );

        OverlayResult mockResult = new OverlayResult(mockPolygons, new byte[0]);

        when(polygonConfig.getPpm()).thenReturn(5.0);
        when(lanePolygonService.process(any(byte[].class), anyDouble())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/polygons")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("test"));
    }

    @Test
    public void testDebugEndpointWithPngFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "data", "test.png", MediaType.IMAGE_PNG_VALUE, "mock image content".getBytes()
        );

        DebugFrames mockFrames = new DebugFrames(
            "road mask".getBytes(),
            "marks mask".getBytes(),
            "bands mask".getBytes(),
            "overlay".getBytes()
        );

        when(lanePolygonService.processWithDebug(any(byte[].class))).thenReturn(mockFrames);

        mockMvc.perform(multipart("/api/polygons/debug")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", "form-data; name=\"attachment\"; filename=\"debug_frames.zip\""));
    }

    @Test
    public void testOverlayEndpointWithResolutionParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "data", "test.png", MediaType.IMAGE_PNG_VALUE, "mock image content".getBytes()
        );

        OverlayResult mockResult = new OverlayResult(
            Collections.emptyList(),
            "mock overlay image".getBytes()
        );

        when(polygonConfig.getPpm()).thenReturn(5.0);
        when(lanePolygonService.process(any(byte[].class), anyDouble())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/polygons/overlay")
                .file(file)
                .param("resolution", "0.10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }
}