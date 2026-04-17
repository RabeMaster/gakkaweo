package com.gakkaweo.backend.support;

import com.gakkaweo.backend.admin.event.AnnouncementEvent;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TestEventCollector {

  private final List<RankingUpdateEvent> rankingEvents = new CopyOnWriteArrayList<>();
  private final List<AnnouncementEvent> announcementEvents = new CopyOnWriteArrayList<>();
  private final List<DayChangeEvent> dayChangeEvents = new CopyOnWriteArrayList<>();

  @EventListener
  public void onRanking(RankingUpdateEvent event) {
    rankingEvents.add(event);
  }

  @EventListener
  public void onAnnouncement(AnnouncementEvent event) {
    announcementEvents.add(event);
  }

  @EventListener
  public void onDayChange(DayChangeEvent event) {
    dayChangeEvents.add(event);
  }

  public List<RankingUpdateEvent> getRankingEvents() {
    return rankingEvents;
  }

  public List<AnnouncementEvent> getAnnouncementEvents() {
    return announcementEvents;
  }

  public List<DayChangeEvent> getDayChangeEvents() {
    return dayChangeEvents;
  }

  public void reset() {
    rankingEvents.clear();
    announcementEvents.clear();
    dayChangeEvents.clear();
  }
}
