import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/search_provider.dart';

class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _companyController = TextEditingController();
  final _titleController = TextEditingController();

  @override
  void dispose() {
    _nameController.dispose();
    _companyController.dispose();
    _titleController.dispose();
    super.dispose();
  }

  Future<void> _onSearch() async {
    if (_formKey.currentState?.validate() != true) {
      return;
    }

    final name = _nameController.text.trim();
    final company = _companyController.text.trim();
    final title = _titleController.text.trim();

    await ref.read(searchProvider.notifier).search(name, company, title);
    if (mounted) {
      final state = ref.read(searchProvider);
      if (state.errorMessage == null) {
        context.go('/results');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(searchProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('LeadFinder')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Search LinkedIn candidates',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 24),
            Form(
              key: _formKey,
              child: Column(
                children: [
                  TextFormField(
                    controller: _nameController,
                    decoration: const InputDecoration(labelText: 'Name'),
                    validator: (value) => value == null || value.isEmpty
                        ? 'Please enter a name'
                        : null,
                  ),
                   const SizedBox(height: 16),
                  TextFormField(
                    controller: _companyController,
                    decoration: const InputDecoration(
                      labelText: 'Company (Optional)',
                      hintText: 'e.g. Google',
                    ),
                    validator: (value) => null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _titleController,
                    decoration: const InputDecoration(
                      labelText: 'Title / Role (Optional)',
                      hintText: 'e.g. Software Engineer',
                    ),
                    validator: (value) => null,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: state.isLoading ? null : _onSearch,
              child: state.isLoading
                  ? const CircularProgressIndicator()
                  : const Text('Search'),
            ),
            if (state.errorMessage != null) ...[
              const SizedBox(height: 12),
              Text(
                state.errorMessage!,
                style: const TextStyle(color: Colors.red),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
