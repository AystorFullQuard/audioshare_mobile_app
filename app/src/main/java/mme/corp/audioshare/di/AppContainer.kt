package mme.corp.audioshare.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import mme.corp.audioshare.data.dto.UserDto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import mme.corp.audioshare.BuildConfig
import mme.corp.audioshare.data.api.CallsApi
import mme.corp.audioshare.data.api.InvitesApi
import mme.corp.audioshare.data.repository.CallRepository
import mme.corp.audioshare.data.repository.InviteRepository
import mme.corp.audioshare.network.realtime.RealtimeSignalingClient
import mme.corp.audioshare.room.OnlineCallCoordinator
import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.api.DevicesApi
import mme.corp.audioshare.data.api.PresenceApi
import mme.corp.audioshare.data.api.RoomsApi
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.repository.BootstrapRepository
import mme.corp.audioshare.data.repository.DeviceRepository
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
import mme.corp.audioshare.runtime.guardAuthenticatedRuntime
import mme.corp.audioshare.startup.BootstrapStartupCoordinator
import mme.corp.audioshare.startup.DeviceRegistrationCoordinator

class AppContainer(
    val sessionManager: SessionManager
) {

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val currentUser = MutableStateFlow<UserDto?>(null)

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

    private val devicesApi: DevicesApi =
        ApiClient.create(DevicesApi::class.java)

    private val presenceApi: PresenceApi =
        ApiClient.create(PresenceApi::class.java)

    private val roomsApi: RoomsApi =
        ApiClient.create(RoomsApi::class.java)

    // GAP-09/10: available to future call/room coordinators, never auto-connected.
    val inviteRepository =
        InviteRepository(ApiClient.create(InvitesApi::class.java), sessionManager)
    val callRepository = CallRepository(ApiClient.create(CallsApi::class.java))
    val realtimeSignalingClient = RealtimeSignalingClient(sessionManager, sessionManager)
    val onlineCallCoordinator = OnlineCallCoordinator(callRepository, realtimeSignalingClient)

    val bootstrapRepository = BootstrapRepository(
        bootstrapApi = bootstrapApi,
        installationIdStore = sessionManager,
        logger = appLogger
    )

    val deviceRepository = DeviceRepository(
        devicesApi = devicesApi,
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
            heartbeatIntervalMillis = BuildConfig.PRESENCE_HEARTBEAT_INTERVAL_MILLIS,
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

    private val deviceRegistrationCoordinator =
        DeviceRegistrationCoordinator(
            deviceRepository = deviceRepository,
            deviceIdStore = sessionManager,
            installationIdStore = sessionManager,
            logger = appLogger
        )

    val bootstrapStartupCoordinator =
        BootstrapStartupCoordinator(
            deviceRegistrationResolver = deviceRegistrationCoordinator,
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
        roomSessionRuntimeController = roomSessionCoordinator,
        disconnectSignaling = { realtimeSignalingClient.close() }
    )

    init {
        applicationScope.guardAuthenticatedRuntime(
            accessTokens = sessionManager.accessToken,
            presenceRuntimeController = presenceLifecycleManager,
            roomSessionRuntimeController = roomSessionCoordinator
        )
    }
}
