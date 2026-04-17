package com.ppiyaki.chat.repository;

import com.ppiyaki.chat.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
