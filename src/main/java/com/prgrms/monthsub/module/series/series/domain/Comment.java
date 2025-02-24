package com.prgrms.monthsub.module.series.series.domain;

import com.prgrms.monthsub.common.domain.BaseEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comment")
public class Comment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", columnDefinition = "BIGINT")
  private Long id;

  @Column(name = "user_id", columnDefinition = "BIGINT", nullable = false)
  private Long userId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "series_id", referencedColumnName = "id")
  private Series series;

  @Column(name = "contents", columnDefinition = "VARCHAR(200)", nullable = false)
  private String contents;

  @Enumerated(EnumType.STRING)
  @Column(name = "comment_status", columnDefinition = "VARCHAR(50)", nullable = false)
  private CommentStatus commentStatus;

  @Builder
  private Comment(
    Long userId,
    Series series,
    String contents,
    CommentStatus commentStatus
  ) {
    this.userId = userId;
    this.series = series;
    this.contents = contents;
    this.commentStatus = commentStatus;
  }

  public enum CommentStatus {
    CREATED,
    DELETED;

    public static CommentStatus of(String commentStatus) {
      return CommentStatus.valueOf(commentStatus.toUpperCase());
    }
  }

}
