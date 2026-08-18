package mme.corp.audioshare.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.api.PresenceApi
import mme.corp.audioshare.data.api.RoomsApi
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.repository.BootstrapRepository
import mme.corp.audioshare.data.repository.PresenceRepository
import mme.corp.audioshare.data.repository.RoomRepository
import mme.corp.audioshare.data.storage.SessionManager
import mme.corp.audioshare.logging.AndroidAppLogger
import mme.corp.audioshare.network.retrofit.ApiClient
import mme.corp.audioshare.presence.AppPresenceLifecycleObserver
import mme.corp.audioshare.presence.DefaultPresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceLifecycleManager
import mme.corp.audioshare.room.DefaultRoomSessionCoordinator
import mme.corp.audioshare.runtime.SessionRuntimeReconciler
import mme.corp.audioshare.startup.BootstrapStartupCoordinator

class AppContainer(
    val sessionManager: SessionManager
) {

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appLogger = AndroidAppLogger

    val accessTokenState: StateFlow<String?> =
        sessionManager.accessToken.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val authApi: AuthApi =
        ApiClient.create(AuthApi::class.java)

    private val bootstrapApi: BootstrapApi =
        ApiClient.create(BootstrapApi::class.java)

    private val presenceApi: PresenceApi =
        ApiClient.create(PresenceApi::class.java)

    private val roomsApi: RoomsApi =
        ApiClient.create(RoomsApi::class.java)

    val bootstrapRepository = BootstrapRepository(
        bootstrapApi = bootstrapApi,
        deviceIdStore = sessionManager,
        logger = appLogger
    )

    val presenceRepository = PresenceRepository(
        presenceApi = presenceApi,
        deviceIdStore = sessionManager
    )

    val roomRepository = RoomRepository(
        roomsApi = roomsApi,
        deviceIdStore = sessionManager,
        logger = appLogger
    )

    val presenceHeartbeatCoordinator =
        DefaultPresenceHeartbeatCoordinator(
            presenceClient = presenceRepository,
            scope = applicationScope,
            logger = appLogger
        )

    val roomSessionCoordinator =
        DefaultRoomSessionCoordinator(
            roomClient = roomRepository,
            presenceCoordinator = presenceHeartbeatCoordinator,
            logger = appLogger
        )

    val presenceLifecycleManager =
        PresenceLifecycleManager(
            heartbeatCoordinator = presenceHeartbeatCoordinator,
            logger = appLogger
        )

    val sessionRuntimeReconciler =
        SessionRuntimeReconciler(
            roomSessionCoordinator = roomSessionCoordinator,
            heartbeatCoordinator = presenceHeartbeatCoordinator,
            presenceRuntimeController = presenceLifecycleManager,
            scope = applicationScope,
            logger = appLogger
        )

    val presenceLifecycleObserver =
        AppPresenceLifecycleObserver(
            lifecycleManager = presenceLifecycleManager,
            sessionRuntimeReconciler = sessionRuntimeReconciler
        )

    val bootstrapStartupCoordinator =
        BootstrapStartupCoordinator(
            deviceBootstrapper = bootstrapRepository,
            sessionBootstrapLoader = bootstrapRepository,
            roomSessionRestorer = roomSessionCoordinator,
            roomSessionRuntimeController = roomSessionCoordinator,
            heartbeatCoordinator = presenceHeartbeatCoordinator,
            presenceRuntimeController = presenceLifecycleManager,
            logger = appLogger
        )

    val authRepository = AuthRepository(
        authApi = authApi,
        sessionManager = sessionManager,
        presenceRuntimeController = presenceLifecycleManager,
        roomSessionRuntimeController = roomSessionCoordinator
    )
}
