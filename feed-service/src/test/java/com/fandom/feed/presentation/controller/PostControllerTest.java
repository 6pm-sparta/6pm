package com.fandom.feed.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.config.CommonAuthAutoConfiguration;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.config.CommonAutoConfiguration;
import com.fandom.feed.application.PostService;
import com.fandom.feed.global.aspect.AuthorizationAspect;
import com.fandom.feed.global.constant.UserRole;
import com.fandom.feed.presentation.dto.request.PostRequest;
import com.fandom.feed.presentation.dto.response.PostResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnableAspectJAutoProxy
@WebMvcTest(PostController.class)
@Import({AuthorizationAspect.class, CommonAutoConfiguration.class, CommonAuthAutoConfiguration.class})
class PostControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    private UserIdCard creatorIdCard;

    @BeforeEach
    void setUp() {
        creatorIdCard = UserIdCard.of(UUID.randomUUID(), UserRole.CREATOR.name());
    }

    private MockHttpServletRequestBuilder withIdCard(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, creatorIdCard);
    }

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {
        @Test
        @DisplayName("정상 요청 - 201 반환")
        void createPostSuccess() throws Exception {
            // given
            String key1 = "posts/20240101/" + UUID.randomUUID() + ".jpg";
            String key2 = "posts/20240101/" + UUID.randomUUID() + ".jpg";

            PostRequest request = new PostRequest("내용", List.of(key1, key2));
            when(postService.createPost(any(), any(), any())).thenReturn(mock(PostResponse.Create.class));

            // when & then
            mockMvc.perform(withIdCard(post("/api/v1/feeds/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("인증 없음 - 401 반환")
        void createPostUnauthorized() throws Exception {
            // given
            PostRequest request = new PostRequest("내용", List.of());

            // when & then
            mockMvc.perform(post("/api/v1/feeds/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("CREATOR 권한 없음 - 403 반환")
        void createPostForbidden() throws Exception {
            // given
            PostRequest request = new PostRequest("내용", List.of());
            UserIdCard memberIdCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");

            // when & then
            mockMvc.perform(post("/api/v1/feeds/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, memberIdCard))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("content 없음 - 400 반환")
        void createPostInvalidRequest() throws Exception {
            // given
            PostRequest request = new PostRequest(null, List.of());

            // when & then
            mockMvc.perform(withIdCard(post("/api/v1/feeds/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {
        @Test
        @DisplayName("정상 요청 - 200 반환")
        void updatePostSuccess() throws Exception {
            // given
            UUID postId = UUID.randomUUID();
            String key = "posts/20240101/" + UUID.randomUUID() + ".jpg";

            PostRequest request = new PostRequest("수정된 내용", List.of(key));
            when(postService.updatePost(any(), any(), any(), any())).thenReturn(mock(PostResponse.Update.class));

            // when & then
            mockMvc.perform(withIdCard(put("/api/v1/feeds/posts/{postId}", postId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("@RequestBody 없으면 content null - 500 반환")
        void updatePostMissingBody() throws Exception {
            // given
            UUID postId = UUID.randomUUID();

            // when & then
            mockMvc.perform(withIdCard(put("/api/v1/feeds/posts/{postId}", postId)))
                    .andExpect(status().is5xxServerError());
        }
    }
}