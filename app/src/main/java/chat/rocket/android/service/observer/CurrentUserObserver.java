package chat.rocket.android.service.observer;

import android.content.Context;
import io.realm.Realm;
import io.realm.RealmResults;

import java.util.ArrayList;
import java.util.List;
import chat.rocket.android.api.MethodCallHelper;
import chat.rocket.android.helper.LogIfError;
import chat.rocket.persistence.realm.models.ddp.RealmUser;
import chat.rocket.persistence.realm.RealmHelper;
import chat.rocket.android.service.DDPClientRef;
import chat.rocket.android.service.Registrable;
import chat.rocket.android.service.ddp.stream.StreamNotifyUserSubscriptionsChanged;
import hugo.weaving.DebugLog;

/**
 * observe the user with emails.
 */
public class CurrentUserObserver extends AbstractModelObserver<RealmUser> {
  private final MethodCallHelper methodCall;
  private boolean currentUserExists;
  private ArrayList<Registrable> listeners;

  public CurrentUserObserver(Context context, String hostname,
                             RealmHelper realmHelper, DDPClientRef ddpClientRef) {
    super(context, hostname, realmHelper, ddpClientRef);
    methodCall = new MethodCallHelper(realmHelper, ddpClientRef);
    currentUserExists = false;
  }

  @Override
  public RealmResults<RealmUser> queryItems(Realm realm) {
    return RealmUser.queryCurrentUser(realm).findAll();
  }

  @Override
  public void onUpdateResults(List<RealmUser> results) {
    boolean exists = !results.isEmpty();

    if (currentUserExists != exists) {
      if (exists) {
        onLogin(results.get(0));
      } else {
        onLogout();
      }
      currentUserExists = exists;
    }
  }

  @DebugLog
  private void onLogin(RealmUser user) {
    if (listeners != null) {
      onLogout();
    }
    listeners = new ArrayList<>();

    final String userId = user.getId();

    // get and observe Room subscriptions.
    methodCall.getRoomSubscriptions().onSuccess(task -> {
      if (listeners != null) {
        Registrable listener = new StreamNotifyUserSubscriptionsChanged(
            context, hostname, realmHelper, ddpClientRef, userId);
        listener.register();
        listeners.add(listener);
      }
      return null;
    }).continueWith(new LogIfError());
  }

  @DebugLog
  private void onLogout() {
    if (listeners != null) {
      for (Registrable listener : listeners) {
        listener.unregister();
      }
    }
    listeners = null;
  }
}
