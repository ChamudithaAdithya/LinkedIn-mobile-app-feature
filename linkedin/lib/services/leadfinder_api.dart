import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../models/candidate.dart';
import '../models/search_request.dart';
import '../models/search_response.dart';
import '../models/select_request.dart';
import '../models/select_response.dart';

class LeadfinderApi {
  /// Set the actual backend host when running on a device.
  ///
  /// Example:
  ///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
  ///   flutter run --dart-define=API_BASE_URL=http://192.168.43.91:8080
  ///   flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080
  static const String _envBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );
  static const List<String> _defaultBaseUrls = [
    'http://10.0.2.2:8080',
    'http://127.0.0.1:8080',
    'http://192.168.43.91:8080',
  ];

  final Dio _dio;
  String? _resolvedBaseUrl;

  LeadfinderApi()
    : _dio = Dio(
        BaseOptions(
          baseUrl: '',
          contentType: Headers.jsonContentType,
          headers: {'Content-Type': Headers.jsonContentType},
          connectTimeout: const Duration(seconds: 30),
          receiveTimeout: const Duration(seconds: 60),
        ),
      ) {
    if (kDebugMode) {
      _dio.interceptors.add(
        LogInterceptor(requestBody: true, responseBody: true),
      );
    }
  }

  Options get _jsonOptions => Options(contentType: Headers.jsonContentType);

  bool _isConnectivityFailure(DioException error) {
    return error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.receiveTimeout ||
        error.type == DioExceptionType.sendTimeout ||
        error.type == DioExceptionType.connectionError ||
        (error.type == DioExceptionType.unknown &&
            error.error is SocketException);
  }

  Future<Response> _post(String path, Object data) async {
    final payload = jsonEncode(data);
    final candidates = <String>[];

    if (_envBaseUrl.isNotEmpty) {
      candidates.add(_envBaseUrl);
    } else {
      if (_resolvedBaseUrl != null) {
        candidates.add(_resolvedBaseUrl!);
      }
      candidates.addAll(_defaultBaseUrls);
    }

    for (final baseUrl in candidates) {
      final url = '$baseUrl$path';
      try {
        if (kDebugMode) {
          debugPrint('Attempting backend request to $url');
        }
        final response = await _dio.post(
          url,
          data: payload,
          options: _jsonOptions,
        );
        _resolvedBaseUrl = baseUrl;
        return response;
      } on DioException catch (error) {
        if (!_isConnectivityFailure(error) || baseUrl == candidates.last) {
          rethrow;
        }
        if (kDebugMode) {
          debugPrint(
            'Backend connection failed for $baseUrl: ${error.message}',
          );
        }
      }
    }

    throw DioException(
      requestOptions: RequestOptions(path: path),
      error: 'Unable to reach backend at any configured host.',
    );
  }

  Future<List<Candidate>> searchCandidates(SearchRequest request) async {
    final response = await _post('/api/v1/search', request.toJson());
    return SearchResponse.fromJson(
      response.data as Map<String, dynamic>,
    ).candidates;
  }

  Future<SelectResponse> selectCandidate(SelectRequest request) async {
    final response = await _post('/api/v1/select', request.toJson());
    return SelectResponse.fromJson(response.data as Map<String, dynamic>);
  }
}
