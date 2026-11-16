package community.zomi;

import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
class MailOutbox {
  final Api api;

  MailOutbox(Api api) {
    this.api = api;
  }

  @Scheduled(fixedDelay = 10000)
  void deliver() {
    api.db().update("delete from mail_outbox where created_at<now()-interval '30 minutes'");
    for (var r : api.db().queryForList("select * from mail_outbox order by created_at limit 10")) {
      try {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(api.from());
        m.setTo((String) r.get("recipient"));
        m.setSubject("Reset your Zomi Nest password");
        m.setText((String) r.get("body"));
        api.mail().send(m);
        api.db().update("delete from mail_outbox where id=?", (UUID) r.get("id"));
      } catch (org.springframework.mail.MailException ignored) {
        /* Keep queued. Do not log token-bearing exception messages. */
      }
    }
  }
}
