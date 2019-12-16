/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.core;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.impl.UseCaseAttachState;
import androidx.camera.testing.DeferrableSurfacesUtil;
import androidx.camera.testing.fakes.FakeAppConfig;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.fakes.FakeCameraInfoInternal;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class UseCaseAttachStateTest {
    private final CameraDevice mMockCameraDevice = Mockito.mock(CameraDevice.class);
    private final CameraCaptureSession mMockCameraCaptureSession =
            Mockito.mock(CameraCaptureSession.class);

    private String mCameraId;

    @Before
    public void setUp() {
        CameraXConfig cameraXConfig = FakeAppConfig.create();
        CameraFactory cameraFactory = cameraXConfig.getCameraFactory(/*valueIfMissing=*/ null);
        try {
            mCameraId = cameraFactory.cameraIdForLensFacing(CameraSelector.LENS_FACING_BACK);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to attach to camera with LensFacing " + CameraSelector.LENS_FACING_BACK,
                    e);
        }
        CameraX.initialize(ApplicationProvider.getApplicationContext(), cameraXConfig);
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException {
        CameraX.shutdown().get();
    }

    @Test
    public void setSingleUseCaseOnline() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config =
                new FakeUseCaseConfig.Builder()
                        .setTargetName("UseCase")
                        .getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseOnline(fakeUseCase);

        SessionConfig.ValidatingBuilder builder = useCaseAttachState.getOnlineBuilder();
        SessionConfig sessionConfig = builder.build();
        List<Surface> surfaces = DeferrableSurfacesUtil.surfaceList(sessionConfig.getSurfaces());
        assertThat(surfaces).containsExactly(fakeUseCase.mSurface);

        for (CameraDevice.StateCallback callback : sessionConfig.getDeviceStateCallbacks()) {
            callback.onOpened(mMockCameraDevice);
        }
        verify(fakeUseCase.mDeviceStateCallback, times(1)).onOpened(mMockCameraDevice);

        for (CameraCaptureSession.StateCallback callback
                : sessionConfig.getSessionStateCallbacks()) {
            callback.onConfigured(mMockCameraCaptureSession);
        }
        verify(fakeUseCase.mSessionStateCallback, times(1)).onConfigured(mMockCameraCaptureSession);

        for (CameraCaptureCallback callback : sessionConfig.getRepeatingCameraCaptureCallbacks()) {
            callback.onCaptureCompleted(null);
        }
        verify(fakeUseCase.mCameraCaptureCallback, times(1)).onCaptureCompleted(null);
    }

    @Test
    public void setTwoUseCasesOnline() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config0 = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase0 = new TestUseCase(config0, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);
        FakeUseCaseConfig config1 = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase1 = new TestUseCase(config1, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseOnline(fakeUseCase0);
        useCaseAttachState.setUseCaseOnline(fakeUseCase1);

        SessionConfig.ValidatingBuilder builder = useCaseAttachState.getOnlineBuilder();
        SessionConfig sessionConfig = builder.build();
        List<Surface> surfaces = DeferrableSurfacesUtil.surfaceList(sessionConfig.getSurfaces());
        assertThat(surfaces).containsExactly(fakeUseCase0.mSurface, fakeUseCase1.mSurface);

        for (CameraDevice.StateCallback callback : sessionConfig.getDeviceStateCallbacks()) {
            callback.onOpened(mMockCameraDevice);
        }
        verify(fakeUseCase0.mDeviceStateCallback, times(1)).onOpened(mMockCameraDevice);
        verify(fakeUseCase1.mDeviceStateCallback, times(1)).onOpened(mMockCameraDevice);

        for (CameraCaptureSession.StateCallback callback
                : sessionConfig.getSessionStateCallbacks()) {
            callback.onConfigured(mMockCameraCaptureSession);
        }
        verify(fakeUseCase0.mSessionStateCallback, times(1)).onConfigured(
                mMockCameraCaptureSession);
        verify(fakeUseCase1.mSessionStateCallback, times(1)).onConfigured(
                mMockCameraCaptureSession);

        for (CameraCaptureCallback callback : sessionConfig.getRepeatingCameraCaptureCallbacks()) {
            callback.onCaptureCompleted(null);
        }
        verify(fakeUseCase0.mCameraCaptureCallback, times(1)).onCaptureCompleted(null);
        verify(fakeUseCase1.mCameraCaptureCallback, times(1)).onCaptureCompleted(null);
    }

    @Test
    public void setUseCaseActiveOnly() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseActive(fakeUseCase);

        SessionConfig.ValidatingBuilder builder = useCaseAttachState.getActiveAndOnlineBuilder();
        SessionConfig sessionConfig = builder.build();
        assertThat(sessionConfig.getSurfaces()).isEmpty();

        for (CameraDevice.StateCallback callback : sessionConfig.getDeviceStateCallbacks()) {
            callback.onOpened(mMockCameraDevice);
        }
        verify(fakeUseCase.mDeviceStateCallback, never()).onOpened(mMockCameraDevice);

        for (CameraCaptureSession.StateCallback callback
                : sessionConfig.getSessionStateCallbacks()) {
            callback.onConfigured(mMockCameraCaptureSession);
        }
        verify(fakeUseCase.mSessionStateCallback, never()).onConfigured(mMockCameraCaptureSession);

        for (CameraCaptureCallback callback : sessionConfig.getRepeatingCameraCaptureCallbacks()) {
            callback.onCaptureCompleted(null);
        }
        verify(fakeUseCase.mCameraCaptureCallback, never()).onCaptureCompleted(null);
    }

    @Test
    public void setUseCaseActiveAndOnline() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseOnline(fakeUseCase);
        useCaseAttachState.setUseCaseActive(fakeUseCase);

        SessionConfig.ValidatingBuilder builder = useCaseAttachState.getActiveAndOnlineBuilder();
        SessionConfig sessionConfig = builder.build();
        List<Surface> surfaces = DeferrableSurfacesUtil.surfaceList(sessionConfig.getSurfaces());
        assertThat(surfaces).containsExactly(fakeUseCase.mSurface);

        for (CameraDevice.StateCallback callback : sessionConfig.getDeviceStateCallbacks()) {
            callback.onOpened(mMockCameraDevice);
        }
        verify(fakeUseCase.mDeviceStateCallback, times(1)).onOpened(mMockCameraDevice);

        for (CameraCaptureSession.StateCallback callback
                : sessionConfig.getSessionStateCallbacks()) {
            callback.onConfigured(mMockCameraCaptureSession);
        }
        verify(fakeUseCase.mSessionStateCallback, times(1)).onConfigured(mMockCameraCaptureSession);

        for (CameraCaptureCallback callback : sessionConfig.getRepeatingCameraCaptureCallbacks()) {
            callback.onCaptureCompleted(null);
        }
        verify(fakeUseCase.mCameraCaptureCallback, times(1)).onCaptureCompleted(null);
    }

    @Test
    public void setUseCaseOffline() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseOnline(fakeUseCase);
        useCaseAttachState.setUseCaseOffline(fakeUseCase);

        SessionConfig.ValidatingBuilder builder = useCaseAttachState.getOnlineBuilder();
        SessionConfig sessionConfig = builder.build();
        assertThat(sessionConfig.getSurfaces()).isEmpty();

        for (CameraDevice.StateCallback callback : sessionConfig.getDeviceStateCallbacks()) {
            callback.onOpened(mMockCameraDevice);
        }
        verify(fakeUseCase.mDeviceStateCallback, never()).onOpened(mMockCameraDevice);

        for (CameraCaptureSession.StateCallback callback
                : sessionConfig.getSessionStateCallbacks()) {
            callback.onConfigured(mMockCameraCaptureSession);
        }
        verify(fakeUseCase.mSessionStateCallback, never()).onConfigured(mMockCameraCaptureSession);

        for (CameraCaptureCallback callback : sessionConfig.getRepeatingCameraCaptureCallbacks()) {
            callback.onCaptureCompleted(null);
        }
        verify(fakeUseCase.mCameraCaptureCallback, never()).onCaptureCompleted(null);
    }

    @Test
    public void setUseCaseInactive() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseOnline(fakeUseCase);
        useCaseAttachState.setUseCaseActive(fakeUseCase);
        useCaseAttachState.setUseCaseInactive(fakeUseCase);

        SessionConfig.ValidatingBuilder builder = useCaseAttachState.getActiveAndOnlineBuilder();
        SessionConfig sessionConfig = builder.build();
        assertThat(sessionConfig.getSurfaces()).isEmpty();

        for (CameraDevice.StateCallback callback : sessionConfig.getDeviceStateCallbacks()) {
            callback.onOpened(mMockCameraDevice);
        }
        verify(fakeUseCase.mDeviceStateCallback, never()).onOpened(mMockCameraDevice);

        for (CameraCaptureSession.StateCallback callback
                : sessionConfig.getSessionStateCallbacks()) {
            callback.onConfigured(mMockCameraCaptureSession);
        }
        verify(fakeUseCase.mSessionStateCallback, never()).onConfigured(mMockCameraCaptureSession);

        for (CameraCaptureCallback callback : sessionConfig.getRepeatingCameraCaptureCallbacks()) {
            callback.onCaptureCompleted(null);
        }
        verify(fakeUseCase.mCameraCaptureCallback, never()).onCaptureCompleted(null);
    }

    @Test
    public void updateUseCase() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_BACK_CAMERA,
                mCameraId);

        useCaseAttachState.setUseCaseOnline(fakeUseCase);
        useCaseAttachState.setUseCaseActive(fakeUseCase);

        // The original template should be PREVIEW.
        SessionConfig firstSessionConfig = useCaseAttachState.getActiveAndOnlineBuilder().build();
        assertThat(firstSessionConfig.getTemplateType()).isEqualTo(CameraDevice.TEMPLATE_PREVIEW);

        // Change the template to STILL_CAPTURE.
        SessionConfig.Builder builder = new SessionConfig.Builder();
        builder.setTemplateType(CameraDevice.TEMPLATE_STILL_CAPTURE);
        fakeUseCase.attachToCamera(mCameraId, builder.build());

        useCaseAttachState.updateUseCase(fakeUseCase);

        // The new template should be STILL_CAPTURE.
        SessionConfig secondSessionConfig = useCaseAttachState.getActiveAndOnlineBuilder().build();
        assertThat(secondSessionConfig.getTemplateType())
                .isEqualTo(CameraDevice.TEMPLATE_STILL_CAPTURE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setUseCaseOnlineWithWrongCamera() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_FRONT_CAMERA,
                mCameraId);

        // Should throw IllegalArgumentException
        useCaseAttachState.setUseCaseOnline(fakeUseCase);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setUseCaseActiveWithWrongCamera() {
        UseCaseAttachState useCaseAttachState = new UseCaseAttachState(mCameraId);
        FakeUseCaseConfig config = new FakeUseCaseConfig.Builder().setTargetName(
                "UseCase").getUseCaseConfig();
        TestUseCase fakeUseCase = new TestUseCase(config, CameraSelector.DEFAULT_FRONT_CAMERA,
                mCameraId);

        // Should throw IllegalArgumentException
        useCaseAttachState.setUseCaseActive(fakeUseCase);
    }

    private static class TestUseCase extends FakeUseCase {
        private final Surface mSurface = Mockito.mock(Surface.class);
        private final CameraDevice.StateCallback mDeviceStateCallback =
                Mockito.mock(CameraDevice.StateCallback.class);
        private final CameraCaptureSession.StateCallback mSessionStateCallback =
                Mockito.mock(CameraCaptureSession.StateCallback.class);
        private final CameraCaptureCallback mCameraCaptureCallback =
                Mockito.mock(CameraCaptureCallback.class);

        TestUseCase(FakeUseCaseConfig config, CameraSelector selector, String cameraId) {
            super(config);
            String selectedCameraId = CameraX.getCameraWithCameraSelector(selector);
            onBind(new FakeCamera(selectedCameraId, null,
                    new FakeCameraInfoInternal(selectedCameraId, 0,
                            selector.getLensFacing())));
            Map<String, Size> suggestedResolutionMap = new HashMap<>();
            suggestedResolutionMap.put(cameraId, new Size(640, 480));
            updateSuggestedResolution(suggestedResolutionMap);
        }

        @Override
        @NonNull
        protected Map<String, Size> onSuggestedResolutionUpdated(
                @NonNull Map<String, Size> suggestedResolutionMap) {
            SessionConfig.Builder builder = new SessionConfig.Builder();
            builder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            builder.addSurface(new ImmediateSurface(mSurface));
            builder.addDeviceStateCallback(mDeviceStateCallback);
            builder.addSessionStateCallback(mSessionStateCallback);
            builder.addRepeatingCameraCaptureCallback(mCameraCaptureCallback);

            attachToCamera(getBoundCameraId(), builder.build());

            return suggestedResolutionMap;
        }
    }
}
